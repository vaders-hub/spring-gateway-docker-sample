package com.example.gateway.auth.controller;

import com.example.gateway.config.security.ConditionalOnDemoIssuer;
import com.example.gateway.auth.dto.TokenRequest;
import com.example.gateway.auth.dto.TokenResponse;
import com.example.gateway.auth.service.TokenService;
import com.example.gateway.common.api.ApiResponse;
import com.example.gateway.common.api.ApiResponses;
import com.example.gateway.common.api.SuccessCode;
import com.example.gateway.common.web.RequestContext;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

// 1단계: 데모 인증 HTTP 진입점. profile과 enabled 조건으로 local/dev/test에서만 Bean을 만든다.
// 운영 OIDC 서버를 구현한 것이 아니며 입력 검증 후 발급 책임은 TokenService에 위임한다.
@RestController
@ConditionalOnDemoIssuer
@RequestMapping("/auth")
public class AuthController {

    private final TokenService tokenService;

    public AuthController(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    @PostMapping("/token")
    public ResponseEntity<ApiResponse<TokenResponse>> token(
            @Valid @RequestBody TokenRequest request,
            ServerWebExchange exchange) {
        String requestId = RequestContext.requestId(exchange);
        // TOKEN_ISSUED가 토큰 응답의 no-store/no-cache 헤더를 함께 지정한다.
        return ApiResponses.success(SuccessCode.TOKEN_ISSUED, tokenService.issue(request), requestId);
    }
}
