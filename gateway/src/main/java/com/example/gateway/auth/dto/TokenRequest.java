package com.example.gateway.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TokenRequest(
        @NotBlank @Size(max = 100) String username,
        @NotBlank @Size(max = 256) String password) {
    @Override
    public String toString() {
        return "TokenRequest[credentials=[REDACTED]]";
    }
}
