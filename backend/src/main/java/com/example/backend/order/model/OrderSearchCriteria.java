package com.example.backend.order.model;

import java.math.BigDecimal;

public record OrderSearchCriteria(Long memberId, String productName, BigDecimal minimumTotal, int page, int size) {
    public OrderSearchCriteria {
        if (memberId != null && memberId < 1 || page < 0 || page > 10000 || size < 1 || size > 100
                || minimumTotal != null && minimumTotal.signum() < 0
                || productName != null && productName.length() > 100) {
            throw new IllegalArgumentException("Invalid order search criteria");
        }
        productName = productName == null || productName.isBlank() ? null : productName.strip();
    }
    public long offset() { return (long) page * size; }
}
