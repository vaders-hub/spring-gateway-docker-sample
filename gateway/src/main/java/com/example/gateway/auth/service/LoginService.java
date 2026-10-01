package com.example.gateway.auth.service;

import com.example.gateway.auth.repository.LoginSessions;
import com.example.gateway.common.config.ConditionalOnDemoIssuer;
import java.time.Duration;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@ConditionalOnDemoIssuer
public class LoginService {
    private final TokenService tokens;
    private final LoginSessions sessions;
    public LoginService(TokenService tokens, LoginSessions sessions) {
        this.tokens = tokens;
        this.sessions = sessions;
    }
    public Mono<TokenService.IssuedToken> login(String username, String password) {
        // 발급은 성공했어도 공유 저장소 등록 실패 시 토큰을 반환하지 않는다.
        return Mono.fromSupplier(() -> tokens.issue(username, password))
                .flatMap(token -> sessions.activate(token.accessToken(), Duration.ofSeconds(token.expiresIn()))
                        .thenReturn(token));
    }
    public Mono<Void> logout(String authenticatedToken) {
        // 현재 Bearer 토큰 한 개만 종료한다. 다른 기기의 로그인까지 종료하지 않는다.
        return sessions.deactivate(authenticatedToken);
    }
}
