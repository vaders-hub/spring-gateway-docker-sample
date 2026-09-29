package com.example.backend.common.exception;

import com.example.backend.common.code.ErrorCode;

// 응답으로 공개할 수 있는 공통 코드만 전달한다. 입력값이나 내부 예외 메시지는 싣지 않는다.
public class BusinessException extends RuntimeException {
    private final ErrorCode code;
    public BusinessException(ErrorCode code) {
        super(code.name());
        this.code = code;
    }
    public ErrorCode code() { return code; }
}
