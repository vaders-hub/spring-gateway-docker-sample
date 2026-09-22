package com.example.backend.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// 1단계: 빈 name/범위 밖 value를 HTTP 경계에서 거부한다. 테스트용 잘못된 입력은 여기서 400으로 연결된다.
public record EchoRequest(
        @NotBlank @Size(max = 100) String name,
        @NotNull @Min(0) @Max(1_000_000) Integer value) {
}
