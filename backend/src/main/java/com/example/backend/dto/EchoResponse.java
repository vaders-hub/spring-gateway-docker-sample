package com.example.backend.dto;

public record EchoResponse(
        String service,
        EchoRequest received,
        String requestId,
        String gatewayUser) {
}
