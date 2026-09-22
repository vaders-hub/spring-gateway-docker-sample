package com.example.backend.common.error;

import com.example.backend.common.api.ApiResponses;
import com.example.backend.common.web.RequestContext;
import jakarta.servlet.http.HttpServletRequest;
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
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// 1·6단계: Controller 처리/입력 검증 오류의 공통 응답 계약. Security filter 오류는 별도 writer가 처리한다.
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> handleValidation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request) {
        ResponseEntity<ProblemDetail> response = problem(ErrorCode.INVALID_REQUEST, request);
        response.getBody().setProperty("errors", exception.getFieldErrors().stream()
                .map(error -> Map.of(
                        "field", error.getField(),
                        "message", error.getDefaultMessage() == null
                                ? "invalid value"
                                : error.getDefaultMessage()))
                .toList());
        return response;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ProblemDetail> handleUnreadableRequest(
            HttpMessageNotReadableException exception,
            HttpServletRequest request) {
        var response = problem(ErrorCode.INVALID_REQUEST, request);
        response.getBody().setDetail("The request body is missing or malformed.");
        return response;
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(
            Exception exception,
            HttpServletRequest request) {
        if (exception instanceof AccessDeniedException) {
            return problem(ErrorCode.ACCESS_DENIED, request);
        }
        // 405 등 프레임워크의 4xx와 Allow 같은 응답 헤더를 보존해 잘못된 500 변환을 피한다.
        if (exception instanceof ErrorResponse error && error.getStatusCode().is4xxClientError()) {
            HttpStatusCode status = error.getStatusCode();
            HttpStatus knownStatus = HttpStatus.resolve(status.value());
            ResponseEntity<ProblemDetail> response = problem(ErrorCode.INVALID_REQUEST, request);
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
        String requestId = requestId(request);
        // 예상치 못한 오류는 requestId와 예외 타입으로 추적한다. 민감값이 섞일 수 있는 원문은 출력하지 않는다.
        log.atError()
                .addKeyValue("exceptionType", exception.getClass().getName())
                .addKeyValue("requestId", requestId)
                .addKeyValue("path", request.getRequestURI())
                .log("unhandled_request_error");
        return problem(ErrorCode.INTERNAL_ERROR, request);
    }

    private ResponseEntity<ProblemDetail> problem(ErrorCode code, HttpServletRequest request) {
        var response = ApiResponses.fail(code, requestId(request));
        response.getBody().setInstance(URI.create(request.getRequestURI()));
        return response;
    }

    private String requestId(HttpServletRequest request) {
        Object requestId = request.getAttribute(RequestContext.REQUEST_ID);
        return requestId == null ? "unknown" : requestId.toString();
    }
}
