package com.example.backend.order.web.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

// 수정 가능한 값은 수량뿐이다. 소유자와 주문 당시 가격은 변경하지 않는다.
public record OrderUpdateRequest(@NotNull @Min(1) @Max(100) Integer quantity) {}
