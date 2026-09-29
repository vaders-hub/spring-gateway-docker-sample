package com.example.gateway.auth.infrastructure.security;

import com.example.gateway.auth.application.port.LoginSessions;
import com.example.gateway.common.security.token.ActiveTokenStore;
import java.time.Duration;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/** 로그인 쓰기와 공통 JWT 검증 읽기가 동일한 저장 계약을 사용하도록 연결한다. */
@Component
class RedisLoginSessions implements LoginSessions {
    private final ActiveTokenStore tokens;
    RedisLoginSessions(ActiveTokenStore tokens) { this.tokens = tokens; }
    public Mono<Void> activate(String token, Duration ttl) { return tokens.activate(token, ttl); }
    public Mono<Void> deactivate(String token) { return tokens.deactivate(token); }
}
