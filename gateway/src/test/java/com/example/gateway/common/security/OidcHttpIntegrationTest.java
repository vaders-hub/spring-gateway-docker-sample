package com.example.gateway.common.security;

import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

/** 실제 RSA 서명/JWKS HTTP 조회/웹 필터를 검증한다. Redis나 Keycloak 컨테이너는 필요 없다. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"app.learning.mock-enabled=true", "management.otlp.metrics.export.enabled=false"})
@ActiveProfiles({"test", "oidc"})
@org.springframework.context.annotation.Import(OidcHttpIntegrationTest.ProbeConfiguration.class)
class OidcHttpIntegrationTest {
    private static final RSAKey KEY;
    private static final HttpServer JWKS;
    private static final String ISSUER = "https://issuer.example.test/realms/lab";
    static {
        try {
            KEY = new RSAKeyGenerator(2048).keyID("test-rsa").generate();
            JWKS = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            JWKS.createContext("/certs", exchange -> {
                byte[] body = new JWKSet(KEY.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                try (var output = exchange.getResponseBody()) { output.write(body); }
            });
            JWKS.start();
        } catch (Exception exception) { throw new ExceptionInInitializerError(exception); }
    }
    @AfterAll static void stopJwks() { JWKS.stop(0); }
    @LocalServerPort int port;
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    com.example.gateway.common.security.token.ActiveTokenStore tokens;
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("OIDC_ISSUER", () -> ISSUER);
        registry.add("OIDC_JWK_SET_URI", () -> "http://127.0.0.1:" + JWKS.getAddress().getPort() + "/certs");
    }
    @Test void validatesPublicKeyAndDoesNotUseDemoTokenStore() throws Exception {
        var response = get("/api/oidc-probe", token(List.of("api.read"), "gateway-sample-api", ISSUER, 300, KEY));
        assertThat(response.statusCode()).isEqualTo(200);
        verifyNoInteractions(tokens);
    }
    @Test void rejectsMissingExpiredWrongIssuerAudienceAndSignature() throws Exception {
        assertThat(get("/api/oidc-probe", null).statusCode()).isEqualTo(401);
        for (String value : List.of(
                token(List.of("api.read"), "another-api", ISSUER, 300, KEY),
                token(List.of("api.read"), "gateway-sample-api", "https://other.example", 300, KEY),
                token(List.of("api.read"), "gateway-sample-api", ISSUER, -300, KEY),
                token(List.of("api.read"), "gateway-sample-api", ISSUER, 300,
                        new RSAKeyGenerator(2048).keyID("test-rsa").generate()))) {
            assertThat(get("/api/oidc-probe", value).statusCode()).isEqualTo(401);
        }
        verifyNoInteractions(tokens);
    }
    @Test void ignoresRequestedScopeAndUsesOnlyTrustedPermissionsClaim() throws Exception {
        // token() intentionally includes an admin scope string; permissions remain the source of authority.
        assertThat(get("/api/oidc-probe", token(List.of(), "gateway-sample-api", ISSUER, 300, KEY)).statusCode()).isEqualTo(403);
    }
    @Test void legacyPasswordLoginIsDisabledInOidcProfile() throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/auth/login"))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString("{}")).build();
        try (var client = HttpClient.newHttpClient()) {
            assertThat(client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(404);
        }
    }

    @Test void rejectsTokensWithoutAnApplicationSubject() throws Exception {
        for (String subject : new String[]{null, ""}) {
            var original = SignedJWT.parse(token(List.of("api.read"), "gateway-sample-api", ISSUER, 300, KEY));
            var claims = new JWTClaimsSet.Builder(original.getJWTClaimsSet()).subject(subject).build();
            var jwt = new SignedJWT(original.getHeader(), claims);
            jwt.sign(new RSASSASigner(KEY));
            assertThat(get("/api/oidc-probe", jwt.serialize()).statusCode()).isEqualTo(401);
        }
    }
    private String token(List<String> permissions, String audience, String issuer, long ttl, RSAKey key) throws Exception {
        var now = Instant.now();
        var claims = new JWTClaimsSet.Builder().issuer(issuer).audience(audience).subject("oidc-user")
                .issueTime(Date.from(now.minusSeconds(600))).expirationTime(Date.from(now.plusSeconds(ttl)))
                .claim("permissions", permissions).claim("scope", "api.read api.write member.admin").build();
        var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(), claims);
        jwt.sign(new RSASSASigner(key));
        return jwt.serialize();
    }
    private HttpResponse<String> get(String path, String token) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).timeout(Duration.ofSeconds(10));
        if (token != null) request.header("Authorization", "Bearer " + token);
        try (var client = HttpClient.newHttpClient()) {
            return client.send(request.GET().build(), HttpResponse.BodyHandlers.ofString());
        }
    }
    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods = false)
    static class ProbeConfiguration {
        @org.springframework.context.annotation.Bean Probe probe() { return new Probe(); }
    }
    @org.springframework.boot.test.context.TestComponent
    @org.springframework.web.bind.annotation.RestController
    static class Probe {
        @org.springframework.web.bind.annotation.GetMapping("/api/oidc-probe")
        Map<String, String> get() { return Map.of("result", "ok"); }
    }

}
