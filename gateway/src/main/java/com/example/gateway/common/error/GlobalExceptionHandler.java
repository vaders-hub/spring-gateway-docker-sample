package com.example.gateway.common.error;

import com.example.gateway.auth.service.InvalidCredentialsException;
import com.example.gateway.common.web.RequestContext;
import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebInputException;

// 1·6단계: Controller 처리/입력 검증 오류의 공통 응답 계약. Security filter 오류는 별도 writer가 처리한다.
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class GlobalExceptionHandler {

    @ExceptionHandler(InvalidCredentialsException.class)
    ResponseEntity<ProblemDetail> handleInvalidCredentials(
            InvalidCredentialsException exception,
            ServerWebExchange exchange) {
        return problem(ErrorCode.INVALID_CREDENTIALS, exchange);
    }

    @ExceptionHandler(WebExchangeBindException.class)
    ResponseEntity<ProblemDetail> handleValidation(
            WebExchangeBindException exception,
            ServerWebExchange exchange) {
        ResponseEntity<ProblemDetail> response = problem(ErrorCode.INVALID_REQUEST, exchange);
        response.getBody().setProperty("errors", exception.getFieldErrors().stream()
                .map(error -> Map.of(
                        "field", error.getField(),
                        "message", error.getDefaultMessage() == null
                                ? "invalid value"
                                : error.getDefaultMessage()))
                .toList());
        return response;
    }

    @ExceptionHandler(ServerWebInputException.class)
    ResponseEntity<ProblemDetail> handleUnreadableRequest(
            ServerWebInputException exception,
            ServerWebExchange exchange) {
        var response = problem(ErrorCode.INVALID_REQUEST, exchange);
        response.getBody().setDetail("The request body is missing or malformed.");
        return response;
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(
            Exception exception,
            ServerWebExchange exchange) {
        return GatewayErrorResponses.from(exchange, exception);
    }

    private ResponseEntity<ProblemDetail> problem(ErrorCode code, ServerWebExchange exchange) {
        return ProblemDetails.forCode(code, requestId(exchange), exchange.getRequest().getPath().value());
    }

    private String requestId(ServerWebExchange exchange) {
        return RequestContext.requestId(exchange);
    }
}
