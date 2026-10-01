package com.example.backend.order.web.dto.request;

import jakarta.validation.constraints.*;

public record OrderPreviewRequest(@NotNull @Positive Long memberId,
                                  @NotNull @Positive Long productId,
                                  @NotNull @Min(1) @Max(100) Integer quantity) {}
