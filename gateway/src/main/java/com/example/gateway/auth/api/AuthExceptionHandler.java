package com.example.gateway.auth.api;

import com.example.gateway.common.config.ConditionalOnDemoIssuer;
import com.example.gateway.auth.application.InvalidCredentialsException;
import com.example.platform.code.CommonErrorCode;
import com.example.platform.exception.ProblemDetails;
import com.example.gateway.common.web.RequestContext;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ServerWebExchange;

// 인증 기능의 예외만 처리한다. DTO/JSON 검증 오류는 common의 Advice에 맡긴다.
@RestControllerAdvice(basePackageClasses = AuthController.class)
@ConditionalOnDemoIssuer
@Order(Ordered.HIGHEST_PRECEDENCE)
class AuthExceptionHandler {
    @ExceptionHandler(com.example.gateway.common.security.token.TokenStoreUnavailableException.class)
    ResponseEntity<ProblemDetail> handleUnavailable(ServerWebExchange exchange) {
        return ProblemDetails.forCode(CommonErrorCode.SERVICE_UNAVAILABLE, RequestContext.requestId(exchange),
                exchange.getRequest().getPath().value());
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    ResponseEntity<ProblemDetail> handleInvalidCredentials(
            InvalidCredentialsException exception, ServerWebExchange exchange) {
        return ProblemDetails.forCode(com.example.gateway.auth.api.AuthErrorCode.INVALID_CREDENTIALS, RequestContext.requestId(exchange),
                exchange.getRequest().getPath().value());
    }
}
