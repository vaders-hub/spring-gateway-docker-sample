package com.example.backend.order.error;
import com.example.platform.code.ErrorCode;
import org.springframework.http.HttpStatus;
// 업무 오류 코드는 기능별 error 패키지에서 정의한다. model과 service의 공개 인터페이스에는 HTTP 타입을 노출하지 않는다.
public enum OrderErrorCode implements ErrorCode {
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "Order not found", "The requested order was not found."),
    INVALID_MEMBER_REFERENCE(HttpStatus.UNPROCESSABLE_CONTENT, "Invalid member reference", "The referenced member does not exist."),
    INVALID_PRODUCT_REFERENCE(HttpStatus.UNPROCESSABLE_CONTENT, "Invalid product reference", "The referenced product does not exist.");
    private final HttpStatus status;
    private final String title;
    private final String detail;
    OrderErrorCode(HttpStatus status, String title, String detail) {
        this.status = status; this.title = title; this.detail = detail;
    }
    public String code() { return name(); }
    public HttpStatus status() { return status; }
    public String title() { return title; }
    public String detail() { return detail; }
}
