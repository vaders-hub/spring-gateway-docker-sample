package com.example.gateway.common.error;

import com.example.gateway.auth.service.InvalidCredentialsException;
import com.example.gateway.common.api.ApiResponses;
import com.example.gateway.common.web.RequestContext;
import java.net.URI;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebInputException;

// 1·6단계: Controller 처리/입력 검증 오류의 공통 응답 계약. Security filter 오류는 별도 writer가 처리한다.
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

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
        if (exception instanceof AccessDeniedException) {
            return problem(ErrorCode.ACCESS_DENIED, exchange);
        }
        // 405 등 프레임워크의 4xx와 Allow 같은 응답 헤더를 보존해 잘못된 500 변환을 피한다.
        if (exception instanceof ErrorResponse error && error.getStatusCode().is4xxClientError()) {
            HttpStatusCode status = error.getStatusCode();
            HttpStatus knownStatus = HttpStatus.resolve(status.value());
            ResponseEntity<ProblemDetail> response = problem(ErrorCode.INVALID_REQUEST, exchange);
            ProblemDetail body = response.getBody();
            // 공통 코드의 기본 400보다 프레임워크가 결정한 실제 4xx 상태를 우선한다.
            body.setStatus(status.value());
            body.setTitle(knownStatus == null ? "Request rejected" : knownStatus.getReasonPhrase());
            body.setDetail("The request could not be processed.");
            return ResponseEntity.status(status)
                    .headers(error.getHeaders())
                    .headers(response.getHeaders())
                    .body(body);
        }
        String requestId = requestId(exchange);
        // 예상치 못한 오류는 requestId와 예외 타입으로 추적한다. 민감값이 섞일 수 있는 원문은 출력하지 않는다.
        log.atError()
                .addKeyValue("exceptionType", exception.getClass().getName())
                .addKeyValue("requestId", requestId)
                .addKeyValue("path", exchange.getRequest().getPath().value())
                .log("unhandled_request_error");
        return problem(ErrorCode.INTERNAL_ERROR, exchange);
    }

    private ResponseEntity<ProblemDetail> problem(ErrorCode code, ServerWebExchange exchange) {
        var response = ApiResponses.fail(code, requestId(exchange));
        response.getBody().setInstance(URI.create(exchange.getRequest().getPath().value()));
        return response;
    }

    private String requestId(ServerWebExchange exchange) {
        String requestId = exchange.getRequest().getHeaders()
                .getFirst(RequestContext.REQUEST_ID_HEADER);
        return requestId == null ? "unknown" : requestId;
    }
}
