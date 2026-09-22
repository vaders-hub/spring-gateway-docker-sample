package com.example.backend.common.api;

import org.springframework.http.HttpStatus;

// 성공 종류별 HTTP 상태와 캐시 정책. JSON 본문의 data/meta 계약과는 별개이다.
public enum SuccessCode {
    OK(HttpStatus.OK, false),
    CREATED(HttpStatus.CREATED, false),
    TOKEN_ISSUED(HttpStatus.OK, true);

    private final HttpStatus status;
    private final boolean noStore;

    SuccessCode(HttpStatus status, boolean noStore) {
        this.status = status;
        this.noStore = noStore;
    }

    public HttpStatus status() {
        return status;
    }

    public boolean noStore() {
        return noStore;
    }
}
