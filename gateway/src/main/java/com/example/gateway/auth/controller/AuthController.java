package com.example.gateway.auth.controller;

import com.example.gateway.auth.dto.TokenRequest;
import com.example.gateway.auth.dto.TokenResponse;
import com.example.gateway.auth.service.TokenService;
import com.example.gateway.common.api.ApiResponse;
import com.example.gateway.common.web.RequestContext;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

// 1단계: 데모 인증 HTTP 진입점. profile과 enabled 조건으로 local/dev/test에서만 Bean을 만든다.
// 운영 OIDC 서버를 구현한 것이 아니며 입력 검증 후 발급 책임은 TokenService에 위임한다.
@RestController
@Profile({"local", "dev", "test"})
@RequestMapping("/auth")
@ConditionalOnProperty(
        prefix = "app.security.demo-user",
        name = "enabled",
        havingValue = "true")
public class AuthController {

    private final TokenService tokenService;

    public AuthController(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    @PostMapping("/token")
    public ResponseEntity<ApiResponse<TokenResponse>> token(
            @Valid @RequestBody TokenRequest request,
            ServerWebExchange exchange) {
        String requestId = exchange.getRequest().getHeaders()
                .getFirst(RequestContext.REQUEST_ID_HEADER);
        // 토큰 응답은 브라우저/프록시 캐시에 남기지 않도록 no-store/no-cache를 지정한다.
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header("Pragma", "no-cache")
                .body(ApiResponse.success(tokenService.issue(request), requestId));
    }
}
