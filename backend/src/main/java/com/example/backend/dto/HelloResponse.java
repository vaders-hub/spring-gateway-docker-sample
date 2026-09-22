package com.example.backend.dto;

import java.time.OffsetDateTime;

public record HelloResponse(
        String service,
        String message,
        OffsetDateTime time,
        String requestId,
        String gatewayUser) {
}
