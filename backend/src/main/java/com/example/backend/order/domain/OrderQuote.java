package com.example.backend.order.domain;

import java.math.BigDecimal;
import java.util.Objects;

// DB·Spring·HTTP를 모르는 계산 규칙. 금액은 부동소수점 대신 BigDecimal을 사용한다.
public record OrderQuote(long memberId, long productId, String productName, int quantity,
                         BigDecimal unitPrice, String currency) {
    public OrderQuote {
        if (memberId < 1 || productId < 1 || quantity < 1 || quantity > 100) {
            throw new IllegalArgumentException("Invalid quote parameters");
        }
        Objects.requireNonNull(productName);
        Objects.requireNonNull(currency);
        if (Objects.requireNonNull(unitPrice).signum() < 0) {
            throw new IllegalArgumentException("Negative unit price");
        }
    }
    public BigDecimal totalPrice() { return unitPrice.multiply(BigDecimal.valueOf(quantity)); }
}
