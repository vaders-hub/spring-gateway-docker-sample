package com.example.backend.order.api.dto.request;

import jakarta.validation.constraints.*;

// 소유자·가격·총액은 클라이언트가 지정하지 않는다. 소유자는 검증된 JWT, 가격은 DB에서 가져온다.
public record OrderCreateRequest(@NotNull @Positive Long memberId,
        @NotNull @Positive Long productId, @NotNull @Min(1) @Max(100) Integer quantity) {}
