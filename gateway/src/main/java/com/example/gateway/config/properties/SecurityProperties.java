package com.example.gateway.config.properties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

// 6단계: 외부 app.security 설정을 타입으로 묶고 시작 시 필수값/중첩 속성을 검증한다.
@Validated
@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(
        @Valid @NotNull Jwt jwt,
        @Valid @NotNull DemoUser demoUser,
        @Valid @NotNull Cors cors) {

    public record Jwt(
            @NotBlank String issuer,
            @NotBlank String audience,
            @NotBlank @Size(min = 32) String secret,
            @NotNull Duration ttl) {

        // 초 단위 JWT TTL로 변환할 때 0초나 소수초가 되지 않도록 제한한다.
        public Jwt {
            if (ttl != null && (ttl.compareTo(Duration.ofSeconds(1)) < 0 || ttl.getNano() != 0)) {
                throw new IllegalArgumentException("app.security.jwt.ttl must be a positive whole number of seconds");
            }
        }

        @Override
        public String toString() {
            return "Jwt[issuer=" + issuer + ", secret=[REDACTED], ttl=" + ttl + "]";
        }
    }

    public record DemoUser(
            boolean enabled,
            String username,
            String password) {

        public DemoUser {
            if (enabled) {
                if (username == null || username.isBlank()) {
                    throw new IllegalArgumentException(
                            "app.security.demo-user.username is required when demo token issuance is enabled");
                }
                if (password == null || password.length() < 12) {
                    throw new IllegalArgumentException(
                            "app.security.demo-user.password must contain at least 12 characters "
                                    + "when demo token issuance is enabled");
                }
            }
        }

        @Override
        public String toString() {
            return "DemoUser[enabled=" + enabled + ", credentials=[REDACTED]]";
        }
    }

    public record Cors(
            @NotNull List<@NotBlank String> allowedOrigins,
            @NotNull Duration maxAge) {

        public Cors {
            allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
            if (allowedOrigins.isEmpty()) {
                throw new IllegalArgumentException(
                        "app.security.cors.allowed-origins must contain at least one origin");
            }
            // CORS는 URL 경로 패턴이 아니라 scheme/host/port로 이루어진 정확한 Origin 목록이다.
            for (String origin : allowedOrigins) {
                URI uri;
                try {
                    uri = URI.create(origin);
                }
                catch (IllegalArgumentException exception) {
                    throw new IllegalArgumentException("CORS origins must be valid HTTP(S) origins");
                }
                if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) || uri.getHost() == null
                        || uri.getUserInfo() != null || uri.getQuery() != null
                        || uri.getFragment() != null || !uri.getRawPath().isEmpty()
                        || uri.getPort() > 65535) {
                    throw new IllegalArgumentException("CORS origins must be exact HTTP(S) origins without paths or wildcards");
                }
            }
            if (maxAge != null && !maxAge.isPositive()) {
                throw new IllegalArgumentException(
                        "app.security.cors.max-age must be positive");
            }
        }

    }
}
