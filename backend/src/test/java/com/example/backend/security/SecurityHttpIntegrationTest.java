package com.example.backend.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.Filter;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full application context and real signed JWTs, without Docker.
 * Exercises the actual Backend controller and validation stack.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(SecurityHttpIntegrationTest.ErrorFixtures.class)
class SecurityHttpIntegrationTest {
    private static final String SECRET = UUID.randomUUID().toString();
    private static final String ISSUER = "security-http-test";
    private static final String AUDIENCE = "test-api";
    private static final String DEMO_PASSWORD = UUID.randomUUID().toString();
    private static final String PATH = "/hello";

    @LocalServerPort
    private int port;

    @Autowired
    private JsonMapper jsonMapper;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("JWT_ISSUER", () -> ISSUER);
        registry.add("JWT_SECRET", () -> SECRET);
        registry.add("JWT_AUDIENCE", () -> AUDIENCE);
        registry.add("APP_ENVIRONMENT", () -> "test");
    }

    @Test
    void missingTokenReturnsProblemAndBearerChallenge() throws Exception {
        var response = request("GET", PATH, null, null);
        assertProblem(response, 401, "UNAUTHORIZED");
        assertThat(response.headers().firstValue("WWW-Authenticate")).contains("Bearer");
    }

    @Test
    void insufficientScopeReturnsForbidden() throws Exception {
        var response = request("GET", PATH, token("other.scope", AUDIENCE, ISSUER, SECRET, 300), null);
        assertProblem(response, 403, "ACCESS_DENIED");
        assertThat(response.headers().firstValue("WWW-Authenticate")).isEmpty();
    }

    @Test
    void signedReadTokenSucceeds() throws Exception {
        var response = request("GET", PATH, token("api.read", AUDIENCE, ISSUER, SECRET, 300), null);
        assertThat(response.statusCode()).isEqualTo(200);
        Map<?, ?> body = jsonMapper.readValue(response.body(), Map.class);
        assertThat(body.size()).isEqualTo(2);
        assertThat(body.get("data")).isInstanceOf(Map.class);
        Map<?, ?> data = (Map<?, ?>) body.get("data");
        assertThat(data.containsKey("requestId")).isFalse();
        assertThat(data.get("gatewayUser")).isEqualTo("test-user");
        assertThat(Instant.parse((String) data.get("time"))).isNotNull();
        Map<?, ?> meta = (Map<?, ?>) body.get("meta");
        assertThat(meta.get("requestId")).isEqualTo("security-http-test");
        assertThat(meta.get("timestamp")).isNotNull();
    }

    @Test
    void headRequiresReadScope() throws Exception {
        assertThat(request("HEAD", PATH, token("api.read", AUDIENCE, ISSUER, SECRET, 300), null).statusCode())
                .isEqualTo(200);
        assertThat(request("HEAD", PATH, token("other.scope", AUDIENCE, ISSUER, SECRET, 300), null).statusCode())
                .isEqualTo(403);
    }

    @Test
    void wrongOrMissingAudienceIsRejected() throws Exception {
        assertProblem(request("GET", PATH, token("api.read", "other-api", ISSUER, SECRET, 300), null),
                401, "UNAUTHORIZED");
        assertProblem(request("GET", PATH, token("api.read", null, ISSUER, SECRET, 300), null),
                401, "UNAUTHORIZED");
    }

    @Test
    void wrongIssuerSignatureAndExpiredTokenAreRejected() throws Exception {
        assertProblem(request("GET", PATH, token("api.read", AUDIENCE, "other-issuer", SECRET, 300), null),
                401, "UNAUTHORIZED");
        assertProblem(request("GET", PATH,
                token("api.read", AUDIENCE, ISSUER, UUID.randomUUID().toString(), 300), null),
                401, "UNAUTHORIZED");
        assertProblem(request("GET", PATH, token("api.read", AUDIENCE, ISSUER, SECRET, -300), null),
                401, "UNAUTHORIZED");
    }

    @Test
    void writeEndpointRequiresWriteScope() throws Exception {
        String body = jsonMapper.writeValueAsString(Map.of("name", "test", "value", 25));
        String path = "/echo";
        assertProblem(request("POST", path, token("api.read", AUDIENCE, ISSUER, SECRET, 300), body),
                403, "ACCESS_DENIED");
        var response = request("POST", path, token("api.write", AUDIENCE, ISSUER, SECRET, 300), body);
        assertThat(response.statusCode()).isEqualTo(200);
        Map<?, ?> envelope = jsonMapper.readValue(response.body(), Map.class);
        Map<?, ?> data = (Map<?, ?>) envelope.get("data");
        assertThat(data.containsKey("requestId")).isFalse();
        assertThat(data.get("received")).isEqualTo(Map.of("name", "test", "value", 25));
    }

    @Test
    void invalidDtoReturnsCommonProblem() throws Exception {
        var response = request("POST", "/echo", token("api.write", AUDIENCE, ISSUER, SECRET, 300),
                jsonMapper.writeValueAsString(Map.of("name", "", "value", -1)));
        assertProblem(response, 400, "INVALID_REQUEST");
        Map<?, ?> body = jsonMapper.readValue(response.body(), Map.class);
        assertThat(body.get("instance")).isEqualTo("/echo");
        assertThat((List<?>) body.get("errors")).hasSize(2);
    }

    @Test
    void malformedBodyKeepsSafeDetailAndRequestPath() throws Exception {
        var response = request("POST", "/echo", token("api.write", AUDIENCE, ISSUER, SECRET, 300), "{");
        assertProblem(response, 400, "INVALID_REQUEST");
        Map<?, ?> body = jsonMapper.readValue(response.body(), Map.class);
        assertThat(body.get("detail")).isEqualTo("The request body is missing or malformed.");
        assertThat(body.get("instance")).isEqualTo("/echo");
    }

    @Test
    void servletFilterFailureUsesErrorDispatchWithoutAuthenticationMasking() throws Exception {
        var response = request("GET", "/fixture/failure", null, null);
        assertProblem(response, 500, "INTERNAL_ERROR");
        Map<?, ?> body = jsonMapper.readValue(response.body(), Map.class);
        assertThat(body.get("instance")).isEqualTo("/fixture/failure");
        assertThat(response.headers().firstValue("X-Request-Id")).contains("security-http-test");
        assertThat(response.body()).doesNotContain("private-filter-message", "ServletException", "trace");
    }

    @Test
    void sendErrorPreservesStatusAndProtocolHeaders() throws Exception {
        var method = request("GET", "/fixture/method", null, null);
        assertProblem(method, 405, "METHOD_NOT_ALLOWED");
        assertThat(method.headers().firstValue("Allow")).contains("POST");
        var unavailable = request("GET", "/fixture/unavailable", null, null);
        assertProblem(unavailable, 503, "SERVICE_UNAVAILABLE");
        assertThat(unavailable.headers().firstValue("Retry-After")).contains("7");
    }

    @Test
    void errorDispatchUsesJsonEvenForBrowserAccept() throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/fixture/failure"))
                .timeout(Duration.ofSeconds(5)).header("Accept", "text/html")
                .header("X-Request-Id", "security-http-test").GET().build();
        try (var client = HttpClient.newHttpClient()) {
            assertProblem(client.send(request, HttpResponse.BodyHandlers.ofString()), 500, "INTERNAL_ERROR");
        }
    }

    @Test
    void unknownPathAndDirectErrorAccessFollowNormalSecurityPolicy() throws Exception {
        assertProblem(request("GET", "/missing", token("api.read", AUDIENCE, ISSUER, SECRET, 300), null),
                404, "NOT_FOUND");
        assertProblem(request("GET", "/error", null, null), 401, "UNAUTHORIZED");
        assertProblem(request("GET", "/error", token("api.read", AUDIENCE, ISSUER, SECRET, 300), null),
                404, "NOT_FOUND");
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ErrorFixtures {
        // 테스트 컨텍스트에서만 인증 이전 filter 실패/sendError를 만든다. 운영 endpoint는 추가하지 않는다.
        @Bean
        FilterRegistrationBean<Filter> errorFixtureFilter() {
            FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>((request, response, chain) -> {
                var http = (HttpServletRequest) request;
                var servlet = (HttpServletResponse) response;
                switch (http.getRequestURI()) {
                    case "/fixture/failure" -> throw new ServletException("private-filter-message");
                    case "/fixture/method" -> {
                        servlet.setHeader("Allow", "POST");
                        servlet.sendError(405, "private-container-message");
                    }
                    case "/fixture/unavailable" -> {
                        servlet.setHeader("Retry-After", "7");
                        servlet.sendError(503, "private-container-message");
                    }
                    default -> chain.doFilter(request, response);
                }
            });
            registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
            registration.setDispatcherTypes(DispatcherType.REQUEST);
            registration.addUrlPatterns("/fixture/*");
            return registration;
        }
    }

    private HttpResponse<String> request(String method, String path, String token, String body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(5))
                .header("X-Request-Id", "security-http-test");
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        if (body != null) {
            builder.header("Content-Type", "application/json");
        }
        builder.method(method, body == null
                ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
            return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        }
    }

    private void assertProblem(HttpResponse<String> response, int status, String code) {
        assertThat(response.statusCode()).isEqualTo(status);
        assertThat(response.headers().firstValue("Content-Type").orElse(""))
                .startsWith("application/problem+json");
        Map<?, ?> body = jsonMapper.readValue(response.body(), Map.class);
        assertThat(body.get("errorCode")).isEqualTo(code);
        assertThat(body.get("status")).isEqualTo(status);
        assertThat(body.containsKey("properties")).isFalse();
        assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
        assertThat(body.get("requestId")).isEqualTo("security-http-test");
        assertThat(response.body()).doesNotContain(SECRET, DEMO_PASSWORD);
    }

    private static String token(String scope, String audience, String issuer, String secret, long ttl) {
        Instant now = Instant.now();
        var claims = JwtClaimsSet.builder().issuer(issuer).subject("test-user")
                .issuedAt(now.minusSeconds(600)).expiresAt(now.plusSeconds(ttl)).claim("scope", scope);
        if (audience != null) {
            claims.audience(List.of(audience));
        }
        var encoder = new NimbusJwtEncoder(new ImmutableSecret<>(
                new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256")));
        return encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims.build())).getTokenValue();
    }
}
