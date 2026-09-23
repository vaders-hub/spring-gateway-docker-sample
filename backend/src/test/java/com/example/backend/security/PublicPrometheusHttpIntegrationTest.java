package com.example.backend.security;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.observability.prometheus-public=true", "app.security.demo-user.enabled=false",
        "management.otlp.metrics.export.enabled=false"})
@ActiveProfiles("test")
class PublicPrometheusHttpIntegrationTest {
    private static final String SECRET = UUID.randomUUID().toString();
    @LocalServerPort
    int port;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("JWT_ISSUER", () -> "public-prometheus-test");
        registry.add("JWT_SECRET", () -> SECRET);
        registry.add("APP_ENVIRONMENT", () -> "test");
    }

    @Test
    void explicitlyPublicPrometheusCanStillBeScrapedWithoutToken() throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/actuator/prometheus"))
                .timeout(Duration.ofSeconds(10)).GET().build();
        try (var client = HttpClient.newHttpClient()) {
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).contains("jvm_memory_used_bytes");
        }
    }
}
