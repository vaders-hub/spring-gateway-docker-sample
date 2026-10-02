package com.example.backend.order.messaging;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

// Kafka와 HTTP 타입을 포함하지 않는 주문 이벤트 계약. 로그인 정보는 전달하지 않는다.
public record OrderEvent(UUID eventId, UUID orderId, Type type, Integer quantity, Instant occurredAt) {
    public enum Type { CREATED, QUANTITY_UPDATED, DELETED }

    public OrderEvent {
        Objects.requireNonNull(eventId);
        Objects.requireNonNull(orderId);
        Objects.requireNonNull(type);
        Objects.requireNonNull(occurredAt);
        if (type == Type.DELETED ? quantity != null : quantity == null || quantity < 1 || quantity > 100) {
            throw new IllegalArgumentException("Invalid order event quantity");
        }
    }
}
