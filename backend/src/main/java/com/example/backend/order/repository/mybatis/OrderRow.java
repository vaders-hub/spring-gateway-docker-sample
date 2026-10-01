package com.example.backend.order.repository.mybatis;

import com.example.backend.order.model.OrderQuote;
import com.example.backend.order.model.StoredOrder;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

// SQL의 평평한 행과 중첩 업무 모델을 분리한다. Entity나 HTTP DTO로 사용하지 않는다.
public record OrderRow(UUID id, String ownerSubject, long memberId, long productId, String productName,
                       int quantity, BigDecimal unitPrice, String currency, Instant createdAt) {
    static OrderRow from(StoredOrder order) {
        var quote = order.quote();
        return new OrderRow(order.id(), order.ownerSubject(), quote.memberId(), quote.productId(),
                quote.productName(), quote.quantity(), quote.unitPrice(), quote.currency(), order.createdAt());
    }
    StoredOrder toDomain() {
        return new StoredOrder(id, ownerSubject,
                new OrderQuote(memberId, productId, productName, quantity, unitPrice, currency), createdAt);
    }
}
