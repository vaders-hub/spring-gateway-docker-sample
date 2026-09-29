package com.example.gateway.auth.application.port;

import java.time.Duration;
import reactor.core.publisher.Mono;

/** 인증 유스케이스가 요구하는 저장 기능. Redis 구현은 infrastructure에서 연결한다. */
public interface LoginSessions {
    Mono<Void> activate(String token, Duration ttl);
    Mono<Void> deactivate(String token);
}
