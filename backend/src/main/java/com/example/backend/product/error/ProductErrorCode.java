package com.example.backend.product.error;
import com.example.platform.code.ErrorCode;
import org.springframework.http.HttpStatus;
// 업무 오류는 유스케이스 소유다. 순수 domain과 contract에는 HTTP 타입을 노출하지 않는다.
public enum ProductErrorCode implements ErrorCode {
    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "Product not found", "The requested product was not found.");
    private final HttpStatus status;
    private final String title;
    private final String detail;
    ProductErrorCode(HttpStatus status, String title, String detail) {
        this.status = status; this.title = title; this.detail = detail;
    }
    public String code() { return name(); }
    public HttpStatus status() { return status; }
    public String title() { return title; }
    public String detail() { return detail; }
}
