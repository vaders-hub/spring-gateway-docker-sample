package com.example.gateway.auth.application;

import com.example.gateway.common.config.ConditionalOnDemoIssuer;
import com.example.gateway.common.config.properties.SecurityProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.Clock;
import java.util.List;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnDemoIssuer
public class TokenService {

    private final JwtEncoder jwtEncoder;
    private final Clock clock;
    private final String issuer;
    private final String audience;
    private final long ttlSeconds;
    private final String demoUsername;
    private final String demoPassword;

    public TokenService(JwtEncoder jwtEncoder, SecurityProperties properties, Clock clock) {
        this.jwtEncoder = jwtEncoder;
        this.clock = clock;
        this.issuer = properties.jwt().issuer();
        this.audience = properties.jwt().audience();
        this.ttlSeconds = properties.jwt().ttl().toSeconds();
        this.demoUsername = properties.demoUser().username();
        this.demoPassword = properties.demoUser().password();
    }

    public IssuedToken issue(String username, String password) {
        // 사용자명과 비밀번호를 둘 다 비교한 뒤 실패를 동일 예외로 처리한다. 실제 사용자 DB 인증은 후속 과제이다.
        boolean usernameMatches = secureEquals(username, demoUsername);
        boolean passwordMatches = secureEquals(password, demoPassword);
        if (!(usernameMatches & passwordMatches)) {
            throw new InvalidCredentialsException();
        }

        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plusSeconds(ttlSeconds);
        // subject는 인증 사용자/Redis 버킷 key가 되고 scope는 GET·쓰기 API 인가에 사용된다.
        // issuer·audience·만료는 양쪽 JwtConfig의 검증 조건과 일치해야 한다.
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .id(java.util.UUID.randomUUID().toString()) // 같은 초에 로그인해도 토큰별 로그아웃이 가능하다.
                .issuer(issuer)
                .subject(username)
                .audience(List.of(audience))
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim("scope", "api.read api.write")
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String accessToken = jwtEncoder
                .encode(JwtEncoderParameters.from(header, claims))
                .getTokenValue();

        return new IssuedToken(accessToken, "Bearer", ttlSeconds);
    }

    // API DTO와 분리된 발급 결과. 로그에 토큰 원문이 드러나지 않게 한다.
    public record IssuedToken(String accessToken, String tokenType, long expiresIn) {
        @Override
        public String toString() {
            return "IssuedToken[accessToken=[REDACTED], tokenType=" + tokenType + ", expiresIn=" + expiresIn + "]";
        }
    }

    private boolean secureEquals(String actual, String expected) {
        if (actual == null || expected == null) {
            return false;
        }
        return MessageDigest.isEqual(
                actual.getBytes(StandardCharsets.UTF_8),
                expected.getBytes(StandardCharsets.UTF_8));
    }
}
