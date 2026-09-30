package com.example.platform.code;
import org.springframework.http.HttpStatus;
// 기술 오류와 기능별 업무 오류의 공통 계약. 공개 메시지에는 입력값이나 비밀을 포함하지 않는다.
public interface ErrorCode {
    String code();
    HttpStatus status();
    String title();
    String detail();
}
