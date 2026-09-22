package com.example.backend.common.error;

import com.example.backend.common.web.RequestContext;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
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
        ResponseEntity<ProblemDetail> response = problem(
                HttpStatus.BAD_REQUEST,
                "Invalid request",
                "One or more request fields are invalid.",
                ErrorCode.INVALID_REQUEST,
                request);
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
        return problem(
                HttpStatus.BAD_REQUEST,
                "Invalid request",
                "The request body is missing or malformed.",
                ErrorCode.INVALID_REQUEST,
                request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(
            Exception exception,
            HttpServletRequest request) {
        if (exception instanceof AccessDeniedException) {
            return problem(HttpStatus.FORBIDDEN, "Forbidden", "Access to this resource is denied.",
                    ErrorCode.ACCESS_DENIED, request);
        }
        // 405 등 프레임워크의 4xx와 Allow 같은 응답 헤더를 보존해 잘못된 500 변환을 피한다.
        if (exception instanceof ErrorResponse error && error.getStatusCode().is4xxClientError()) {
            HttpStatusCode status = error.getStatusCode();
            HttpStatus knownStatus = HttpStatus.resolve(status.value());
            ResponseEntity<ProblemDetail> response = problem(status,
                    knownStatus == null ? "Request rejected" : knownStatus.getReasonPhrase(),
                    "The request could not be processed.", ErrorCode.INVALID_REQUEST, request);
            return ResponseEntity.status(status)
                    .headers(error.getHeaders())
                    .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                    .body(response.getBody());
        }
        String requestId = requestId(request);
        // 예상치 못한 오류는 requestId와 예외 타입으로 추적한다. 민감값이 섞일 수 있는 원문은 출력하지 않는다.
        log.atError()
                .addKeyValue("exceptionType", exception.getClass().getName())
                .addKeyValue("requestId", requestId)
                .addKeyValue("path", request.getRequestURI())
                .log("unhandled_request_error");
        return problem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal server error",
                "An unexpected error occurred.",
                ErrorCode.INTERNAL_ERROR,
                request);
    }

    private ResponseEntity<ProblemDetail> problem(
            HttpStatusCode status,
            String title,
            String detail,
            ErrorCode errorCode,
            HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create("urn:problem:" + errorCode.name().toLowerCase(Locale.ROOT).replace('_', '-')));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("errorCode", errorCode.name());
        problem.setProperty("requestId", requestId(request));
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    private String requestId(HttpServletRequest request) {
        Object requestId = request.getAttribute(RequestContext.REQUEST_ID);
        return requestId == null ? "unknown" : requestId.toString();
    }
}
