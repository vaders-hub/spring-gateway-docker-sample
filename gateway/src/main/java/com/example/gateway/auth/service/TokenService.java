package com.example.gateway.auth.service;

import com.example.gateway.auth.dto.TokenRequest;
import com.example.gateway.auth.dto.TokenResponse;
import com.example.gateway.config.properties.SecurityProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
@Profile({"local", "dev", "test"})
@ConditionalOnProperty(
        prefix = "app.security.demo-user",
        name = "enabled",
        havingValue = "true")
public class TokenService {

    private final JwtEncoder jwtEncoder;
    private final String issuer;
    private final String audience;
    private final long ttlSeconds;
    private final String demoUsername;
    private final String demoPassword;

    public TokenService(JwtEncoder jwtEncoder, SecurityProperties properties) {
        this.jwtEncoder = jwtEncoder;
        this.issuer = properties.jwt().issuer();
        this.audience = properties.jwt().audience();
        this.ttlSeconds = properties.jwt().ttl().toSeconds();
        this.demoUsername = properties.demoUser().username();
        this.demoPassword = properties.demoUser().password();
    }

    public TokenResponse issue(TokenRequest request) {
        // 사용자명과 비밀번호를 둘 다 비교한 뒤 실패를 동일 예외로 처리한다. 실제 사용자 DB 인증은 후속 과제이다.
        boolean usernameMatches = secureEquals(request.username(), demoUsername);
        boolean passwordMatches = secureEquals(request.password(), demoPassword);
        if (!(usernameMatches & passwordMatches)) {
            throw new InvalidCredentialsException();
        }

        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plusSeconds(ttlSeconds);
        // subject는 인증 사용자/Redis 버킷 key가 되고 scope는 GET·쓰기 API 인가에 사용된다.
        // issuer·audience·만료는 양쪽 JwtConfig의 검증 조건과 일치해야 한다.
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(request.username())
                .audience(List.of(audience))
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim("scope", "api.read api.write")
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String accessToken = jwtEncoder
                .encode(JwtEncoderParameters.from(header, claims))
                .getTokenValue();

        return new TokenResponse(accessToken, "Bearer", ttlSeconds);
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
