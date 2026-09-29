package com.example.gateway.auth.application.dto;

public record TokenResponse(
        String accessToken,
        String tokenType,
        long expiresIn) {
    @Override
    public String toString() {
        return "TokenResponse[accessToken=[REDACTED], tokenType=" + tokenType + ", expiresIn=" + expiresIn + "]";
    }
}
