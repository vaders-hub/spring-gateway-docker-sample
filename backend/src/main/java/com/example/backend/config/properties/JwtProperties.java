package com.example.backend.config.properties;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

// 6단계: Gateway와 동일한 issuer/audience/키를 외부 설정에서 받으며 값 오류를 시작 시 발견한다.
@Validated
@ConfigurationProperties(prefix = "app.security.jwt")
public record JwtProperties(
        @NotBlank String issuer,
        @NotBlank String audience,
        @NotBlank @Size(min = 32) String secret) {
    // 설정 객체를 출력해도 서명 비밀키가 노출되지 않게 record의 기본 toString을 대체한다.
    @Override
    public String toString() {
        return "JwtProperties[issuer=" + issuer + ", secret=[REDACTED]]";
    }
}
