package com.example.gateway.security;

import com.example.gateway.auth.controller.AuthController;
import com.example.gateway.auth.service.TokenService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("prod")
class ProductionContextTest {
    private static final String SECRET = UUID.randomUUID().toString();

    @Autowired
    private ApplicationContext context;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("JWT_ISSUER", () -> "production-context-test");
        registry.add("JWT_SECRET", () -> SECRET);
        registry.add("JWT_AUDIENCE", () -> "test-api");
        registry.add("CORS_ALLOWED_ORIGINS", () -> "https://example.test");
        registry.add("APP_ENVIRONMENT", () -> "test");
        registry.add("PROMETHEUS_PUBLIC", () -> "false");
    }

    @Test
    void productionDoesNotExposeDemoIssuerBeans() {
        assertThat(context.getBeansOfType(AuthController.class)).isEmpty();
        assertThat(context.getBeansOfType(TokenService.class)).isEmpty();
        assertThat(context.getBeansOfType(JwtEncoder.class)).isEmpty();
    }
}
