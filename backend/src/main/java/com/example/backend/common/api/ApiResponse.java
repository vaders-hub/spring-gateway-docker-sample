package com.example.backend.common.api;

import java.time.Instant;

// 성공 응답 본문만 표현한다. HTTP 상태와 헤더는 ApiResponses에서 결정한다.
public record ApiResponse<T>(T data, Meta meta) {

    public record Meta(String requestId, Instant timestamp) {
    }
}
