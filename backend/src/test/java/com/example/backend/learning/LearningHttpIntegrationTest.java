package com.example.backend.learning;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "app.learning.mock-enabled=true", "app.learning.docs-enabled=true"})
@ActiveProfiles("test")
class LearningHttpIntegrationTest {

    // 이 테스트의 관심사는 라우팅/업무/DB이다. 로그아웃 검증은 SecurityHttpIntegrationTest에서 수행한다.
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private com.example.backend.common.security.token.ActiveTokenStore activeTokenStore;
    @org.junit.jupiter.api.BeforeEach
    void configureActiveTokens() {
        org.mockito.Mockito.when(activeTokenStore.isActive(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(true);
    }

    private static final String SECRET = UUID.randomUUID().toString();
    @LocalServerPort int port;
    @Autowired JsonMapper json;
    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("JWT_ISSUER", () -> "learning-test");
        registry.add("JWT_SECRET", () -> SECRET);
        registry.add("JWT_AUDIENCE", () -> "learning-api");
    }
    @Test
    void fixturesAndGeneratedMappersReturnApiContracts() throws Exception {
        var members = request("GET", "/members", "api.read", null);
        assertThat(members.statusCode()).isEqualTo(200);
        assertThat(members.body()).contains("Sample Member", "meta", "learning-test");
        var product = request("GET", "/products/1", "api.read", null);
        assertThat(product.statusCode()).isEqualTo(200);
        assertThat(product.body()).contains("Keyboard", "50000", "KRW");
        assertProblem(request("GET", "/members/999", "api.read", null), 404, "NOT_FOUND");
    }
    @Test
    void orderPreviewUsesCatalogPricesAndNeverCreatesAnOrder() throws Exception {
        String input = "{\"memberId\":1,\"productId\":1,\"quantity\":2}";
        for (int i=0; i<2; i++) {
            var response = request("POST", "/orders/preview", "api.write", input);
            assertThat(response.statusCode()).isEqualTo(200);
            Map<?,?> data = (Map<?,?>)json.readValue(response.body(), Map.class).get("data");
            assertThat(data.get("totalPrice").toString()).isEqualTo("100000");
            assertThat(data.containsKey("orderId")).isFalse();
        }
        assertProblem(request("POST", "/orders/preview", "api.write",
                "{\"memberId\":999,\"productId\":1,\"quantity\":2}"), 404, "NOT_FOUND");
        assertProblem(request("POST", "/orders/preview", "api.write",
                "{\"memberId\":1,\"productId\":999,\"quantity\":2}"), 404, "NOT_FOUND");
    }
    @Test
    void dtoValidationAndJwtScopesRemainEnforced() throws Exception {
        assertProblem(request("POST", "/orders/preview", "api.write", "{}"), 400, "INVALID_REQUEST");
        assertProblem(request("POST", "/orders/preview", "api.write",
                "{\"memberId\":1,\"productId\":1,\"quantity\":101}"), 400, "INVALID_REQUEST");
        assertProblem(request("GET", "/members", null, null), 401, "UNAUTHORIZED");
        assertProblem(request("GET", "/products", "api.write", null), 403, "ACCESS_DENIED");
        assertProblem(request("POST", "/orders/preview", "api.read", "{}"), 403, "ACCESS_DENIED");
        assertProblem(request("DELETE", "/members/1", "api.read api.write", null), 403, "ACCESS_DENIED");
    }
    @Test
    void openApiAndSwaggerUiAreActuallyServedInLearningMode() throws Exception {
        var spec = request("GET", "/v3/api-docs", null, null);
        assertThat(spec.statusCode()).isEqualTo(200);
        var document = json.readValue(spec.body(), Map.class);
        assertThat(((Map<?,?>)document.get("paths")).keySet().stream().map(Object::toString).toList()).containsAll(List.of("/members", "/products/{id}", "/orders/preview"));
        assertThat(spec.body()).contains("bearerAuth", "OrderPreviewRequest", "OrderPreviewResponse");
        var ui = request("GET", "/swagger-ui/index.html", null, null);
        assertThat(ui.statusCode()).isEqualTo(200);
        assertThat(ui.body()).contains("swagger-ui");
    }
    private HttpResponse<String> request(String method, String path, String scope, String body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(20)).header("X-Request-Id", "learning-test");
        if (scope != null) builder.header("Authorization", "Bearer " + token(scope));
        if (body != null) builder.header("Content-Type", "application/json");
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        try (var client = HttpClient.newHttpClient()) {
            return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        }
    }
    private void assertProblem(HttpResponse<String> response, int status, String code) {
        assertThat(response.statusCode()).isEqualTo(status);
        assertThat(response.headers().firstValue("Content-Type").orElse("")).startsWith("application/problem+json");
        Map<?,?> body = json.readValue(response.body(), Map.class);
        assertThat(body.get("errorCode")).isEqualTo(code);
        assertThat(body.get("requestId")).isEqualTo("learning-test");
        assertThat(response.body()).doesNotContain(SECRET);
    }
    private String token(String scope) {
        var claims = JwtClaimsSet.builder().issuer("learning-test").subject("learner")
                .audience(List.of("learning-api")).issuedAt(Instant.now().minusSeconds(10))
                .expiresAt(Instant.now().plusSeconds(300)).claim("scope", scope).build();
        var encoder = new NimbusJwtEncoder(new ImmutableSecret<>(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256")));
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }
}
