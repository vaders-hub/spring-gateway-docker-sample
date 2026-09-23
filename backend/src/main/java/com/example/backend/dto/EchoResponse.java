package com.example.backend.dto;

public record EchoResponse(String service, Received received, String gatewayUser) {
    // 입력 DTO의 검증 규칙/필드 변경이 응답 계약으로 전파되지 않게 출력 타입을 분리한다.
    public record Received(String name, Integer value) {}
}
