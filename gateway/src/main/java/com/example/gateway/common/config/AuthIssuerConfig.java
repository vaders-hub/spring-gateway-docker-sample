package com.example.gateway.common.config;

import com.example.gateway.common.config.properties.SecurityProperties;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

// 데모 토큰 발급 기능만 소유한다. JwtConfig의 검증 decoder는 발급기에 의존하지 않는다.
@Configuration(proxyBeanMethods = false)
@ConditionalOnDemoIssuer
class AuthIssuerConfig {
    @Bean
    JwtEncoder jwtEncoder(SecurityProperties properties) {
        var key = new SecretKeySpec(properties.jwt().secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        return new NimbusJwtEncoder(new ImmutableSecret<>(key));
    }
}
