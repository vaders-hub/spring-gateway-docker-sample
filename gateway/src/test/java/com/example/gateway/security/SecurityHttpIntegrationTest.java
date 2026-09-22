package com.example.gateway.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
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
 * The test-only controller avoids backend/Redis calls; routing and rate limiting require separate tests.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(SecurityHttpIntegrationTest.ProbeConfiguration.class)
class SecurityHttpIntegrationTest {
    private static final String SECRET = UUID.randomUUID().toString();
    private static final String ISSUER = "security-http-test";
    private static final String AUDIENCE = "test-api";
    private static final String DEMO_PASSWORD = UUID.randomUUID().toString();
    private static final String PATH = "/api/security-probe";

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
        registry.add("DEMO_USERNAME", () -> "demo");
        registry.add("DEMO_PASSWORD", () -> DEMO_PASSWORD);
        registry.add("BACKEND_URL", () -> "http://127.0.0.1:1");
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
        assertThat(request("GET", PATH, token("api.read", AUDIENCE, ISSUER, SECRET, 300), null).statusCode())
                .isEqualTo(200);
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
        String path = "/api/security-probe";
        assertProblem(request("POST", path, token("api.read", AUDIENCE, ISSUER, SECRET, 300), body),
                403, "ACCESS_DENIED");
        assertThat(request("POST", path, token("api.write", AUDIENCE, ISSUER, SECRET, 300), body).statusCode())
                .isEqualTo(200);
    }

    @Test
    void demoIssuerAddsAudienceAndBothScopesWithoutCaching() throws Exception {
        var response = request("POST", "/auth/token", null,
                jsonMapper.writeValueAsString(Map.of("username", "demo", "password", DEMO_PASSWORD)));
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
        assertThat(response.headers().firstValue("Pragma")).contains("no-cache");
        Map<?, ?> envelope = jsonMapper.readValue(response.body(), Map.class);
        assertThat(envelope.size()).isEqualTo(2);
        Map<?, ?> meta = (Map<?, ?>) envelope.get("meta");
        assertThat(meta.get("requestId")).isEqualTo("security-http-test");
        assertThat(meta.get("timestamp")).isNotNull();
        Map<?, ?> data = (Map<?, ?>) envelope.get("data");
        String issuedToken = (String) data.get("accessToken");
        assertThat(request("GET", PATH, issuedToken, null).statusCode()).isEqualTo(200);
        assertThat(request("POST", PATH, issuedToken, "{}").statusCode()).isEqualTo(200);
    }

    @Test
    void invalidCredentialsUseSharedErrorPolicy() throws Exception {
        var response = request("POST", "/auth/token", null,
                jsonMapper.writeValueAsString(Map.of("username", "demo", "password", "wrong-password")));
        assertProblem(response, 401, "INVALID_CREDENTIALS");
        assertThat(response.headers().firstValue("WWW-Authenticate")).contains("Bearer");
        assertThat(response.body()).doesNotContain("wrong-password");
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ProbeConfiguration {
        @Bean
        ProbeController securityProbeController() {
            return new ProbeController();
        }
    }

    @org.springframework.boot.test.context.TestComponent
    @RestController
    static class ProbeController {
        @GetMapping("/api/security-probe")
        Map<String, String> read() {
            return Map.of("result", "ok");
        }

        @PostMapping("/api/security-probe")
        Map<String, String> write() {
            return Map.of("result", "ok");
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
