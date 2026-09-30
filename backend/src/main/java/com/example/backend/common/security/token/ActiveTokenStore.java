package com.example.backend.common.security.token;
import com.example.platform.security.token.TokenKey;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

/** Backend는 로그인 feature를 참조하지 않고 공통 인증 저장 계약만 읽는다. */
@Component
public class ActiveTokenStore {
    private final StringRedisTemplate redis;
    public ActiveTokenStore(StringRedisTemplate redis) { this.redis = redis; }
    public boolean isActive(String token) {
        try {
            // Servlet 요청 스레드에서 제한된 timeout으로 조회한다. 장애 시 인증을 우회하지 않는다.
            return Boolean.TRUE.equals(redis.hasKey(TokenKey.of(token)));
        } catch (DataAccessException exception) {
            throw new TokenStoreUnavailableException();
        }
    }
}
