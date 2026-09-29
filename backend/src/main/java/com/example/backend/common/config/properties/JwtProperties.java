package com.example.backend.common.config.properties;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.AssertTrue;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

// 6단계: Gateway와 동일한 issuer/audience/키를 외부 설정에서 받으며 값 오류를 시작 시 발견한다.
@Validated
@ConfigurationProperties(prefix = "app.security.jwt")
public record JwtProperties(
        @NotBlank String issuer,
        @NotBlank String audience,
        String secret,
        String jwkSetUri) {
    @ConstructorBinding
    public JwtProperties { }
    public JwtProperties(String issuer, String audience, String secret) {
        this(issuer, audience, secret, null);
    }
    public boolean usesJwks() { return jwkSetUri != null && !jwkSetUri.isBlank(); }
    @AssertTrue(message = "Configure either a 32+ character demo secret or an HTTP(S) JWKS URI")
    public boolean isKeyConfigurationValid() {
        if (!usesJwks()) return secret != null && secret.length() >= 32;
        try {
            var uri = java.net.URI.create(jwkSetUri);
            return (secret == null || secret.isBlank()) && uri.getHost() != null
                    && ("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
                    && uri.getUserInfo() == null && uri.getFragment() == null;
        } catch (IllegalArgumentException invalid) { return false; }
    }
    // 설정 객체를 출력해도 서명 비밀키가 노출되지 않게 record의 기본 toString을 대체한다.
    @Override
    public String toString() {
        return "JwtProperties[issuer=" + issuer + ", secret=[REDACTED]]";
    }
}
