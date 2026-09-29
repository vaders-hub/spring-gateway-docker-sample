package com.example.backend.learning;

import com.example.backend.order.application.command.PlaceOrderService;
import com.example.backend.order.application.query.OrderQueryService;
import com.example.backend.common.exception.BusinessException;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import javax.crypto.spec.SecretKeySpec;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;

// 매 실행마다 빈 PostgreSQL을 만든다. H2 대체/공유 DB 사용/도커 부재 시 skip을 하지 않는다.
@Tag("database")
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"test", "persistence"})
class PersistenceIntegrationTest {
    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17.11-alpine");
    private static final String SECRET = UUID.randomUUID().toString();
    @LocalServerPort int port;
    @Autowired JsonMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @Autowired Flyway flyway;
    @Autowired PlaceOrderService orders;
    @Autowired OrderQueryService queries;
    @Autowired PlatformTransactionManager transactionManager;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        // Testcontainers가 배정한 임시 포트/자격증명은 이 테스트 ApplicationContext에만 전달된다.
        registry.add("DB_URL", postgres::getJdbcUrl);
        registry.add("DB_USERNAME", postgres::getUsername);
        registry.add("DB_PASSWORD", postgres::getPassword);
        registry.add("JWT_ISSUER", () -> "persistence-test");
        registry.add("JWT_SECRET", () -> SECRET);
        registry.add("JWT_AUDIENCE", () -> "learning-api");
    }

    @BeforeAll
    static void migrationCreatesSchemaWithoutInjectingLearningData(@Autowired Flyway flyway, @Autowired JdbcTemplate jdbc) {
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("1");
        assertThat(jdbc.queryForObject("select count(*) from members", Long.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from products", Long.class)).isZero();
        flyway.validate();
    }

    @BeforeEach
    void resetOnlyDisposableDatabase() {
        jdbc.update("delete from purchase_orders");
        jdbc.update("delete from products");
        jdbc.update("delete from members");
        new ResourceDatabasePopulator(new ClassPathResource("sql/catalog.sql")).execute(dataSource);
    }

    @Test
    void httpCreatesAndReadsOnlyOwnersOrder() throws Exception {
        var created = request("POST", "/orders", "alice", "api.write", input(2));
        assertThat(created.statusCode()).isEqualTo(201);
        var data = data(created);
        String id = data.get("id").toString();
        // JSON number의 소수점 표기는 파서마다 다르므로 문자열 자리수가 아니라 금액을 비교한다.
        assertThat(new java.math.BigDecimal(((Map<?,?>)data.get("quote")).get("totalPrice").toString()))
                .isEqualByComparingTo("100000");
        assertThat(created.body()).doesNotContain("ownerSubject", SECRET);
        assertThat(request("GET", "/orders/"+id, "alice", "api.read", null).statusCode()).isEqualTo(200);
        assertProblem(request("GET", "/orders/"+id, "bob", "api.read", null), 404, "NOT_FOUND");
        assertProblem(request("GET", "/orders/"+id, "alice", "api.write", null), 403, "ACCESS_DENIED");
        assertProblem(request("POST", "/orders", "alice", null, input(2)), 401, "UNAUTHORIZED");
        assertProblem(request("POST", "/orders", "alice", "api.read", input(2)), 403, "ACCESS_DENIED");
        assertThat(jdbc.queryForObject("select count(*) from purchase_orders", Long.class)).isEqualTo(1);
    }

    @Test
    void validationAndMissingCatalogNeverInsertOrders() throws Exception {
        assertProblem(request("POST", "/orders", "alice", "api.write", input(0)), 400, "INVALID_REQUEST");
        assertProblem(request("POST", "/orders", "alice", "api.write",
                "{\"memberId\":999,\"productId\":1,\"quantity\":2}"), 404, "NOT_FOUND");
        assertProblem(request("POST", "/orders", "alice", "api.write",
                "{\"memberId\":1,\"productId\":999,\"quantity\":2}"), 404, "NOT_FOUND");
        assertThat(jdbc.queryForObject("select count(*) from purchase_orders", Long.class)).isZero();
    }

    @Test
    void transactionRollsBackEvenAfterSqlWasFlushed() {
        // HTTP 400만 확인하는 것으로는 rollback 검증이 아니다. 실제 INSERT 후 예외를 발생시킨다.
        var tx = new TransactionTemplate(transactionManager);
        assertThatIllegalStateException().isThrownBy(() -> tx.executeWithoutResult(status -> {
            orders.place(1, 1, 2, "alice");
            assertThat(jdbc.queryForObject("select count(*) from purchase_orders", Long.class)).isEqualTo(1);
            throw new IllegalStateException("Rollback test");
        }));
        assertThat(jdbc.queryForObject("select count(*) from purchase_orders", Long.class)).isZero();
    }

    @Test
    void databaseEnforcesQuantityAndForeignKeysIndependentlyOfDtoValidation() {
        var order = orders.place(1, 1, 1, "alice");
        assertThatThrownBy(() -> jdbc.update("update purchase_orders set quantity=0 where id=?", order.id()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("update purchase_orders set member_id=999 where id=?", order.id()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("delete from products where id=1"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(queries.get(order.id(), "alice").quote().quantity()).isEqualTo(1);
    }

    @Test
    void savedPriceSnapshotSurvivesCatalogChange() throws Exception {
        var order = orders.place(1, 1, 2, "alice");
        jdbc.update("update products set unit_price=60000, name='New Keyboard' where id=1");
        var saved = queries.get(order.id(), "alice");
        assertThat(saved.quote().totalPrice()).isEqualByComparingTo("100000");
        assertThat(saved.quote().productName()).isEqualTo("Keyboard");
        var preview = data(request("POST", "/orders/preview", "alice", "api.write", input(2)));
        assertThat(new java.math.BigDecimal(preview.get("totalPrice").toString())).isEqualByComparingTo("120000");
        assertThat(jdbc.queryForObject("select count(*) from purchase_orders", Long.class)).isEqualTo(1);
        assertThatThrownBy(() -> queries.get(order.id(), "bob")).isInstanceOf(BusinessException.class);
    }

    @Test
    void catalogEndpointsUseJpaAndReadinessIsHealthy() throws Exception {
        jdbc.update("update members set display_name='Persisted Member' where id=1");
        assertThat(request("GET", "/members/1", "alice", "api.read", null).body()).contains("Persisted Member");
        assertThat(request("GET", "/products", "alice", "api.read", null).body()).contains("Keyboard", "Mouse");
        assertThat(request("GET", "/actuator/health/readiness", "alice", null, null).statusCode()).isEqualTo(200);
    }

    private String input(int quantity) {
        return "{\"memberId\":1,\"productId\":1,\"quantity\":"+quantity+"}";
    }
    private Map<?,?> data(HttpResponse<String> response) {
        return (Map<?,?>)json.readValue(response.body(), Map.class).get("data");
    }
    private void assertProblem(HttpResponse<String> response, int status, String code) {
        assertThat(response.statusCode()).isEqualTo(status);
        assertThat(response.headers().firstValue("Content-Type").orElse("")).startsWith("application/problem+json");
        Map<?,?> body = json.readValue(response.body(), Map.class);
        assertThat(body.get("errorCode")).isEqualTo(code);
        assertThat(body.get("requestId")).isEqualTo("persistence-test");
        assertThat(response.body()).doesNotContain(SECRET);
    }
    private HttpResponse<String> request(String method, String path, String subject, String scope, String body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(20)).header("X-Request-Id", "persistence-test");
        if (scope != null) builder.header("Authorization", "Bearer " + token(subject, scope));
        if (body != null) builder.header("Content-Type", "application/json");
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        try (var client = HttpClient.newHttpClient()) {
            return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        }
    }
    private String token(String subject, String scope) {
        var claims = JwtClaimsSet.builder().issuer("persistence-test").subject(subject)
                .audience(List.of("learning-api")).issuedAt(Instant.now().minusSeconds(10))
                .expiresAt(Instant.now().plusSeconds(300)).claim("scope", scope).build();
        var encoder = new NimbusJwtEncoder(new ImmutableSecret<>(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256")));
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }
}
