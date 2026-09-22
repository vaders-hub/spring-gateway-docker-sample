package com.example.gateway.common.error;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.net.URI;
import java.net.ServerSocket;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cloud.gateway.filter.ratelimit.RateLimiter;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.web.server.WebFilter;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "management.otlp.metrics.export.enabled=false")
@ActiveProfiles("test")
@Import(GatewayErrorHttpIntegrationTest.Fixtures.class)
class GatewayErrorHttpIntegrationTest {
    private static final String SECRET = UUID.randomUUID().toString();
    private static final AtomicInteger UPSTREAM_CALLS = new AtomicInteger();
    private static final DisposableServer UPSTREAM = HttpServer.create().host("127.0.0.1").port(0)
            .handle((request, response) -> {
                UPSTREAM_CALLS.incrementAndGet();
                if (request.uri().equals("/slow")) {
                    return Mono.delay(Duration.ofSeconds(3)).then(Mono.defer(() ->
                            response.sendString(Mono.just("late response")).then()));
                }
                if (request.uri().equals("/upstream-error")) {
                    return response.status(503).header("Content-Type", "application/problem+json")
                            .header("Retry-After", "9").sendString(Mono.just(
                                    "{\"status\":503,\"errorCode\":\"UPSTREAM_OWN_ERROR\"}"));
                }
                return response.header("Content-Type", "application/json").sendString(Mono.just("{\"result\":\"ok\"}"));
            }).bindNow();

    // 고정 서비스 포트 대신 사용하지 않는 로컬 포트를 골라 연결 거절을 재현한다.
    private static final int REFUSED_PORT = unusedPort();

    private static int unusedPort() {
        try (var socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
        catch (IOException exception) {
            throw new IllegalStateException("Cannot allocate test port", exception);
        }
    }

    @LocalServerPort
    int port;
    @Autowired
    JsonMapper mapper;
    @MockitoSpyBean
    RedisRateLimiter limiter;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("JWT_ISSUER", () -> "error-test");
        registry.add("JWT_AUDIENCE", () -> "error-api");
        registry.add("JWT_SECRET", () -> SECRET);
        registry.add("DEMO_USERNAME", () -> "demo");
        registry.add("DEMO_PASSWORD", () -> SECRET);
        registry.add("APP_ENVIRONMENT", () -> "test");
        registry.add("BACKEND_URL", () -> "http://127.0.0.1:" + UPSTREAM.port());
        registry.add("BACKEND_RESPONSE_TIMEOUT", () -> "5s");
    }

    @BeforeEach
    void allowRequests() {
        UPSTREAM_CALLS.set(0);
        doReturn(Mono.just(new RateLimiter.Response(true, Map.of("X-RateLimit-Remaining", "4"))))
                .when(limiter).isAllowed(anyString(), anyString());
    }

    @AfterAll
    static void closeUpstream() {
        UPSTREAM.disposeNow();
    }

    @Test
    void quotaDenialReturnsProblemPreservesHeadersAndDoesNotReachBackend() throws Exception {
        doReturn(Mono.just(new RateLimiter.Response(false,
                Map.of("X-RateLimit-Remaining", "0", "X-RateLimit-Burst-Capacity", "5"))))
                .when(limiter).isAllowed(anyString(), anyString());
        var response = request("GET", "/api/ok", "gateway-errors");
        assertProblem(response, 429, "TOO_MANY_REQUESTS", "/api/ok");
        assertThat(response.headers().firstValue("X-RateLimit-Remaining")).contains("0");
        assertThat(response.headers().firstValue("X-RateLimit-Burst-Capacity")).contains("5");
        assertThat(UPSTREAM_CALLS.get()).isZero();
    }

    @Test
    void allowedRouteAndUpstreamOwnedErrorArePassedThrough() throws Exception {
        var ok = request("GET", "/api/ok", "gateway-errors");
        assertThat(ok.statusCode()).isEqualTo(200);
        assertThat(ok.body()).isEqualTo("{\"result\":\"ok\"}");
        var upstreamError = request("GET", "/api/upstream-error", "gateway-errors");
        assertThat(upstreamError.statusCode()).isEqualTo(503);
        assertThat(upstreamError.body()).isEqualTo("{\"status\":503,\"errorCode\":\"UPSTREAM_OWN_ERROR\"}");
        assertThat(upstreamError.headers().firstValue("Retry-After")).contains("9");
        assertThat(UPSTREAM_CALLS.get()).isEqualTo(2);
    }

    @Test
    void upstreamTimeoutReturns504WithOriginalExternalPath() throws Exception {
        assertProblem(request("GET", "/api/slow", "gateway-errors"),
                504, "GATEWAY_TIMEOUT", "/api/slow");
        assertThat(UPSTREAM_CALLS.get()).isEqualTo(1);
    }

    @Test
    void connectionRefusedReturns502() throws Exception {
        assertProblem(request("GET", "/api/refused", "gateway-errors"),
                502, "BAD_GATEWAY", "/api/refused");
    }

    @Test
    void filterExceptionReturns500AndNormalizesRequestId() throws Exception {
        var response = request("GET", "/api/filter-error", "unsafe request id");
        assertProblem(response, 500, "INTERNAL_ERROR", "/api/filter-error");
        String id = response.headers().firstValue("X-Request-Id").orElseThrow();
        assertThat(id).matches("[A-Za-z0-9._-]{1,64}").isNotEqualTo("unsafe request id");
    }

    @Test
    void missingRouteAndWrongMethodPreserve404And405() throws Exception {
        assertProblem(request("GET", "/missing", "gateway-errors"), 404, "NOT_FOUND", "/missing");
        var response = request("GET", "/auth/token", "gateway-errors");
        assertProblem(response, 405, "METHOD_NOT_ALLOWED", "/auth/token");
        assertThat(response.headers().firstValue("Allow").orElseThrow()).contains("POST");
    }

    @Test
    void headErrorHasStatusAndHeadersWithoutBody() throws Exception {
        var response = request("HEAD", "/missing", "gateway-errors");
        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.headers().firstValue("Content-Type").orElseThrow()).startsWith("application/problem+json");
        assertThat(response.body()).isEmpty();
    }

    private void assertProblem(HttpResponse<String> response, int status, String code, String path) {
        assertThat(response.statusCode()).isEqualTo(status);
        assertThat(response.headers().firstValue("Content-Type").orElseThrow()).startsWith("application/problem+json");
        assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
        Map<?, ?> body = mapper.readValue(response.body(), Map.class);
        assertThat(body.get("status")).isEqualTo(status);
        assertThat(body.get("errorCode")).isEqualTo(code);
        assertThat(body.get("instance")).isEqualTo(path);
        assertThat(body.get("requestId")).isEqualTo(response.headers().firstValue("X-Request-Id").orElseThrow());
        assertThat(response.body()).doesNotContain(SECRET, "private-filter-message", "Exception", "localhost", "127.0.0.1");
    }

    private HttpResponse<String> request(String method, String path, String requestId) throws Exception {
        var claims = JwtClaimsSet.builder().issuer("error-test").audience(List.of("error-api"))
                .subject("test-user").issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300))
                .claim("scope", "api.read api.write").build();
        var encoder = new NimbusJwtEncoder(new ImmutableSecret<>(
                new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256")));
        String token = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(10)).header("Authorization", "Bearer " + token)
                .header("X-Request-Id", requestId).method(method, HttpRequest.BodyPublishers.noBody()).build();
        try (var client = HttpClient.newHttpClient()) {
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Fixtures {
        @Bean
        RouteLocator refusedRoute(RouteLocatorBuilder builder) {
            return builder.routes()
                    .route("slow", route -> route.order(-10).path("/api/slow")
                            .filters(filters -> filters.stripPrefix(1))
                            .metadata("response-timeout", 500)
                            .uri("http://127.0.0.1:" + UPSTREAM.port()))
                    .route("refused", route -> route.order(-10)
                    .path("/api/refused").uri("http://127.0.0.1:" + REFUSED_PORT)).build();
        }

        @Bean
        @Order(0)
        WebFilter failingFilter() {
            return (exchange, chain) -> exchange.getRequest().getPath().value().equals("/api/filter-error")
                    ? Mono.error(new IllegalStateException("private-filter-message")) : chain.filter(exchange);
        }
    }
}
