package com.example.backend.order.web.dto.request;

import com.example.backend.order.model.OrderSearchCriteria;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record OrderSearchRequest(@Positive Long memberId, @Size(max = 100) String productName,
                                 @DecimalMin("0") BigDecimal minimumTotal,
                                 @Min(0) @Max(10000) Integer page, @Min(1) @Max(100) Integer size) {
    public OrderSearchCriteria toCriteria() {
        return new OrderSearchCriteria(memberId, productName, minimumTotal, page == null ? 0 : page, size == null ? 20 : size);
    }
}
