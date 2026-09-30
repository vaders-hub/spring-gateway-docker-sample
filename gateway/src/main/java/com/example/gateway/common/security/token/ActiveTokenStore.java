package com.example.gateway.common.security.token;
import com.example.platform.security.token.TokenKey;

import java.time.Duration;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/** 활성 토큰 목록. JVM 캐시 없이 매 요청 조회하므로 모든 인스턴스에 로그아웃이 반영된다. */
@Component
public class ActiveTokenStore {
    private final ReactiveStringRedisTemplate redis;
    public ActiveTokenStore(ReactiveStringRedisTemplate redis) { this.redis = redis; }

    public Mono<Void> activate(String token, Duration ttl) {
        // SET+TTL을 한 명령으로 수행한다. 저장 성공 전에는 로그인 응답을 내보내지 않는다.
        return redis.opsForValue().set(TokenKey.of(token), "1", ttl)
                .filter(Boolean.TRUE::equals)
                .switchIfEmpty(Mono.error(new TokenStoreUnavailableException()))
                .onErrorMap(error -> new TokenStoreUnavailableException()).then();
    }
    public Mono<Boolean> isActive(String token) {
        return redis.hasKey(TokenKey.of(token)).defaultIfEmpty(false)
                .onErrorMap(error -> new TokenStoreUnavailableException());
    }
    public Mono<Void> deactivate(String token) {
        // 이미 만료/삭제된 key의 DEL=0도 성공이다. 호출 전 인증에서는 비활성 토큰을 거부한다.
        return redis.delete(TokenKey.of(token))
                .onErrorMap(error -> new TokenStoreUnavailableException()).then();
    }
}
