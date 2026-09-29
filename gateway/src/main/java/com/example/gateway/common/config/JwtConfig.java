package com.example.gateway.common.config;

import com.example.gateway.common.config.properties.SecurityProperties;
import com.example.gateway.common.security.token.ActiveTokenStore;
import com.example.gateway.common.security.token.TokenStoreUnavailableException;
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
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;

@Configuration(proxyBeanMethods = false)
class JwtConfig {
    private SecretKey jwtSecretKey(SecurityProperties properties) {
        String secret = properties.jwt().secret();
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    @Bean
    ReactiveJwtDecoder jwtDecoder(
            SecurityProperties properties, ActiveTokenStore tokens) {
        // JWKS 위치는 서버 설정에서만 받는다. 토큰 헤더의 임의 URL은 신뢰하지 않는다.
        NimbusReactiveJwtDecoder decoder = properties.jwt().usesJwks()
                ? NimbusReactiveJwtDecoder.withJwkSetUri(properties.jwt().jwkSetUri())
                        .jwsAlgorithm(org.springframework.security.oauth2.jose.jws.SignatureAlgorithm.RS256).build()
                : NimbusReactiveJwtDecoder.withSecretKey(jwtSecretKey(properties)).macAlgorithm(MacAlgorithm.HS256).build();
        // 서명만 맞는 토큰도 다른 API용일 수 있다. audience와 기본 issuer/시간 검증을 함께 적용한다.
        var audienceValidator = new JwtClaimValidator<List<String>>(JwtClaimNames.AUD,
                audiences -> audiences != null && audiences.contains(properties.jwt().audience()));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(properties.jwt().issuer()), audienceValidator,
                // 요청 제한/소유권은 sub에 의존한다. 식별자 없는 토큰은 라우팅 전 401로 거부한다.
                new JwtClaimValidator<String>(JwtClaimNames.SUB, subject -> subject != null && !subject.isBlank())));
        // OIDC는 Keycloak 발급/만료 정책을 따른다. 데모 Redis 활성 목록과 혼합하지 않는다.
        if (properties.jwt().usesJwks()) return decoder;
        // 암호학적 검증 후 공유 활성 목록을 확인한다. Netty에서 block()을 호출하지 않는다.
        return token -> decoder.decode(token).flatMap(jwt -> tokens.isActive(token)
                .onErrorMap(TokenStoreUnavailableException.class,
                        error -> new OAuth2AuthenticationException(new OAuth2Error("temporarily_unavailable"), error))
                .flatMap(active -> active ? reactor.core.publisher.Mono.just(jwt)
                        : reactor.core.publisher.Mono.error(new BadJwtException("Inactive access token"))));
    }

}
