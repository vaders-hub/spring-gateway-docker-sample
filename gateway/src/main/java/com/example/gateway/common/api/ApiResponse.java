package com.example.gateway.common.api;

import java.time.Instant;

public record ApiResponse<T>(T data, Meta meta) {

    public static <T> ApiResponse<T> success(T data, String requestId) {
        return new ApiResponse<>(data, new Meta(requestId, Instant.now()));
    }

    public record Meta(String requestId, Instant timestamp) {
    }
}
