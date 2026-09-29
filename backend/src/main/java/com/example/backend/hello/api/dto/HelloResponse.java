package com.example.backend.hello.api.dto;

import java.time.Instant;

public record HelloResponse(String service, String message, Instant time, String username) {
}
