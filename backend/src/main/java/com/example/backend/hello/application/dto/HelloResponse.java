package com.example.backend.hello.application.dto;

import java.time.Instant;

public record HelloResponse(String service, String message, Instant time, String username) {
}
