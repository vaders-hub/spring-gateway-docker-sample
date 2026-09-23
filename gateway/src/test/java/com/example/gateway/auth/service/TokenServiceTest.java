package com.example.gateway.auth.service;

import com.example.gateway.auth.dto.TokenRequest;
import com.example.gateway.config.properties.SecurityProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TokenServiceTest {
    @Test
    void tokenLifetimeIsBasedOnInjectedClockAndBadCredentialsNeverReachEncoder() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        String password = UUID.randomUUID().toString();
        var properties = new SecurityProperties(
                new SecurityProperties.Jwt("issuer", "audience", UUID.randomUUID().toString(), Duration.ofSeconds(90)),
                new SecurityProperties.DemoUser(true, "alice", password),
                new SecurityProperties.Cors(List.of("https://example.test"), Duration.ofHours(1)));
        var encoder = mock(JwtEncoder.class);
        when(encoder.encode(any())).thenAnswer(invocation -> {
            org.springframework.security.oauth2.jwt.JwtEncoderParameters parameters = invocation.getArgument(0);
            var claims = parameters.getClaims();
            assertThat(claims.getIssuedAt()).isEqualTo(now);
            assertThat(claims.getExpiresAt()).isEqualTo(now.plusSeconds(90));
            assertThat(claims.getAudience()).containsExactly("audience");
            return Jwt.withTokenValue("test-token").header("alg", "HS256")
                    .claims(values -> values.putAll(claims.getClaims())).build();
        });
        var service = new TokenService(encoder, properties, Clock.fixed(now, ZoneOffset.UTC));
        assertThat(service.issue(new TokenRequest("alice", password)).expiresIn()).isEqualTo(90);
        assertThatThrownBy(() -> service.issue(new TokenRequest("alice", "wrong")))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(encoder, times(1)).encode(any());
    }
}
