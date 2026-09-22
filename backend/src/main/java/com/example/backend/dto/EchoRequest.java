package com.example.backend.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record EchoRequest(
        @NotBlank @Size(max = 100) String name,
        @NotNull @Min(0) @Max(1_000_000) Integer value) {
}
