package com.example.backend.common.config;

import com.example.backend.common.config.properties.JwtProperties;
import com.example.backend.common.security.token.ActiveTokenStore;
import com.example.backend.common.security.token.TokenStoreUnavailableException;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

@Configuration(proxyBeanMethods = false)
class JwtConfig {
    private SecretKey jwtSecretKey(JwtProperties properties) {
        byte[] bytes = properties.secret().getBytes(StandardCharsets.UTF_8);
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    @Bean
    JwtDecoder jwtDecoder(JwtProperties properties, ActiveTokenStore tokens) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withSecretKey(jwtSecretKey(properties))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        // 서명만 맞는 토큰도 다른 API용일 수 있다. audience와 기본 issuer/시간 검증을 함께 적용한다.
        var audienceValidator = new JwtClaimValidator<List<String>>(JwtClaimNames.AUD,
                audiences -> audiences != null && audiences.contains(properties.audience()));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(properties.issuer()), audienceValidator));
        return token -> {
            var jwt = decoder.decode(token); // 변조/만료 토큰은 Redis 조회 전에 거부한다.
            try {
                if (!tokens.isActive(token)) throw new BadJwtException("Inactive access token");
            } catch (TokenStoreUnavailableException exception) {
                throw new OAuth2AuthenticationException(new OAuth2Error("temporarily_unavailable"), exception);
            }
            return jwt;
        };
    }
}
