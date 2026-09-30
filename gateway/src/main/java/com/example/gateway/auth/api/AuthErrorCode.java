package com.example.gateway.auth.api;
import com.example.platform.code.ErrorCode;
import org.springframework.http.HttpStatus;
// 데모 로그인 오류는 인증 feature가 소유한다.
public enum AuthErrorCode implements ErrorCode {
    INVALID_CREDENTIALS;
    public String code() { return name(); }
    public HttpStatus status() { return HttpStatus.UNAUTHORIZED; }
    public String title() { return "Unauthorized"; }
    public String detail() { return "The supplied credentials are invalid."; }
}
