package com.example.gateway.auth.api;

import com.example.gateway.auth.api.dto.TokenRequest;
import com.example.gateway.auth.api.dto.TokenResponse;
import com.example.gateway.auth.application.LoginService;
import com.example.gateway.common.config.ConditionalOnDemoIssuer;
import com.example.gateway.common.code.SuccessCode;
import com.example.gateway.common.response.ApiResponse;
import com.example.gateway.common.response.ApiResponses;
import com.example.gateway.common.web.RequestContext;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/** local/dev/test 학습용 인증 API. 운영 OIDC 서버/사용자 DB 인증은 별도 단계이다. */
@RestController
@ConditionalOnDemoIssuer
@RequestMapping("/auth")
public class AuthController {
    private final LoginService loginService;
    public AuthController(LoginService loginService) { this.loginService = loginService; }

    // 기존 실습 호환 경로도 같은 유스케이스를 거쳐 활성 토큰 등록을 반드시 수행한다.
    @PostMapping({"/login", "/token"})
    public Mono<ResponseEntity<ApiResponse<TokenResponse>>> login(
            @Valid @RequestBody TokenRequest request, ServerWebExchange exchange) {
        String requestId = RequestContext.requestId(exchange);
        return loginService.login(request.username(), request.password())
                .map(token -> ApiResponses.success(SuccessCode.TOKEN_ISSUED,
                        new TokenResponse(token.accessToken(), token.tokenType(), token.expiresIn()), requestId));
    }

    @PostMapping("/logout")
    public Mono<ResponseEntity<ApiResponse<Void>>> logout(
            @AuthenticationPrincipal Jwt principal, ServerWebExchange exchange) {
        // 사용자 입력으로 다른 토큰을 삭제하지 않는다. Security가 검증한 현재 토큰만 전달한다.
        String requestId = RequestContext.requestId(exchange);
        return loginService.logout(principal.getTokenValue())
                .thenReturn(ApiResponses.<Void>success(SuccessCode.LOGGED_OUT, null, requestId));
    }
}
