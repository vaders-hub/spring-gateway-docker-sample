package com.example.backend.config.properties;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.security.jwt")
public record JwtProperties(
        @NotBlank String issuer,
        @NotBlank String audience,
        @NotBlank @Size(min = 32) String secret) {
    @Override
    public String toString() {
        return "JwtProperties[issuer=" + issuer + ", secret=[REDACTED]]";
    }
}
