package com.example.gateway.common.error;

import org.springframework.http.HttpStatus;

// 오류 코드의 이름은 외부 계약이다. 메시지는 공개 가능한 고정 문구만 사용한다.
public enum ErrorCode {
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Unauthorized", "Authentication is required."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "Forbidden", "Access to this resource is denied."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Unauthorized", "The supplied credentials are invalid."),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "Invalid request", "One or more request fields are invalid."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error", "An unexpected error occurred.");

    private final HttpStatus status;
    private final String title;
    private final String detail;

    ErrorCode(HttpStatus status, String title, String detail) {
        this.status = status;
        this.title = title;
        this.detail = detail;
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
