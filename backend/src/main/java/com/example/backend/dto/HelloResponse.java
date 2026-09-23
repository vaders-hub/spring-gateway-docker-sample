package com.example.backend.dto;

import java.time.Instant;

public record HelloResponse(String service, String message, Instant time, String gatewayUser) {
}
