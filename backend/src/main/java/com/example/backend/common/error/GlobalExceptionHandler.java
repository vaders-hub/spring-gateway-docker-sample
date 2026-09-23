package com.example.backend.common.error;

import com.example.backend.common.code.ErrorCode;
import com.example.backend.common.util.ErrorDiagnostics;
import com.example.backend.common.web.RequestContext;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.core.Ordered;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.ErrorResponse;

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
        // 프레임워크의 4xx/5xx 상태와 Allow 같은 응답 헤더를 보존한다.
        if (exception instanceof ErrorResponse error && error.getStatusCode().isError()) {
            return ProblemDetails.forStatus(error.getStatusCode(), requestId(request),
                    request.getRequestURI(), error.getHeaders());
        }
        String requestId = requestId(request);
        // 예상치 못한 오류는 requestId와 예외 타입으로 추적한다. 민감값이 섞일 수 있는 원문은 출력하지 않는다.
        log.atError()
                .addKeyValue("exceptionType", exception.getClass().getName())
                .addKeyValue("causes", ErrorDiagnostics.causes(exception))
                .addKeyValue("requestId", requestId)
                .addKeyValue("path", request.getRequestURI())
                .log("unhandled_request_error");
        return problem(ErrorCode.INTERNAL_ERROR, request);
    }

    private ResponseEntity<ProblemDetail> problem(ErrorCode code, HttpServletRequest request) {
        return ProblemDetails.forCode(code, requestId(request), request.getRequestURI());
    }

    private String requestId(HttpServletRequest request) {
        Object requestId = request.getAttribute(RequestContext.REQUEST_ID_ATTRIBUTE);
        return requestId == null ? "unknown" : requestId.toString();
    }
}
