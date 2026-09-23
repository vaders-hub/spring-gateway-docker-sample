package com.example.gateway.common.code;

import org.springframework.http.HttpStatus;

// 오류 코드의 이름은 외부 계약이다. 메시지는 공개 가능한 고정 문구만 사용한다.
public enum ErrorCode {
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Unauthorized", "Authentication is required."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "Forbidden", "Access to this resource is denied."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Unauthorized", "The supplied credentials are invalid."),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "Invalid request", "One or more request fields are invalid."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "Not Found", "The requested resource was not found."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "Method Not Allowed", "The request method is not supported."),
    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "Too Many Requests", "The request limit has been exceeded."),
    BAD_GATEWAY(HttpStatus.BAD_GATEWAY, "Bad Gateway", "The upstream service could not be reached."),
    SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Service Unavailable", "The service is temporarily unavailable."),
    GATEWAY_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "Gateway Timeout", "The upstream service did not respond in time."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error", "An unexpected error occurred.");

    private final HttpStatus status;
    private final String title;
    private final String detail;

    ErrorCode(HttpStatus status, String title, String detail) {
        this.status = status;
        this.title = title;
        this.detail = detail;
    }

    // 기본 코드가 없는 상태도 호출부에서 원래 HTTP 상태를 보존한다.
    public static ErrorCode fromStatus(int status) {
        return switch (status) {
            case 401 -> UNAUTHORIZED;
            case 403 -> ACCESS_DENIED;
            case 404 -> NOT_FOUND;
            case 405 -> METHOD_NOT_ALLOWED;
            case 429 -> TOO_MANY_REQUESTS;
            case 502 -> BAD_GATEWAY;
            case 503 -> SERVICE_UNAVAILABLE;
            case 504 -> GATEWAY_TIMEOUT;
            default -> status >= 400 && status < 500 ? INVALID_REQUEST : INTERNAL_ERROR;
        };
    }

    public HttpStatus status() {
        return status;
    }

    public String title() {
        return title;
    }

    public String detail() {
        return detail;
    }
}
