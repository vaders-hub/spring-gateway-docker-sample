package com.example.backend.order.model;

import java.time.Instant;
import java.util.UUID;

// 구매 시점의 상품 이름/가격은 quote에 고정한다. 이후 카탈로그 가격이 바뀌어도 기존 주문은 변하지 않는다.
public record StoredOrder(UUID id, String ownerSubject, OrderQuote quote, Instant createdAt) {
    public StoredOrder withQuantity(int quantity) {
        var updatedQuote = new OrderQuote(quote.memberId(), quote.productId(), quote.productName(),
                quantity, quote.unitPrice(), quote.currency());
        return new StoredOrder(id, ownerSubject, updatedQuote, createdAt);
    }
}
