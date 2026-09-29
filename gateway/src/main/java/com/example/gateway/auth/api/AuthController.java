package com.example.gateway.auth.api;

import com.example.gateway.auth.api.dto.TokenRequest;
import com.example.gateway.auth.api.dto.TokenResponse;
import com.example.gateway.auth.application.TokenService;
import com.example.gateway.common.response.ApiResponse;
import com.example.gateway.common.response.ApiResponses;
import com.example.gateway.common.code.SuccessCode;
import com.example.gateway.common.web.RequestContext;
import com.example.gateway.common.config.ConditionalOnDemoIssuer;
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
        var issued = tokenService.issue(request.username(), request.password());
        var response = new TokenResponse(issued.accessToken(), issued.tokenType(), issued.expiresIn());
        // TOKEN_ISSUED가 토큰 응답의 no-store/no-cache 헤더를 함께 지정한다.
        return ApiResponses.success(SuccessCode.TOKEN_ISSUED, response, requestId);
    }
}
