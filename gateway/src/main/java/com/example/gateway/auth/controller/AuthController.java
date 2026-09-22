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
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header("Pragma", "no-cache")
                .body(ApiResponse.success(tokenService.issue(request), requestId));
    }
}
