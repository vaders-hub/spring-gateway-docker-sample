package com.example.backend.order.infrastructure;

import com.example.backend.order.domain.OrderQuote;
import com.example.backend.order.domain.StoredOrder;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

// 외부 feature와 JPA 연관관계를 맺지 않고 ID를 저장한다. 참조 무결성은 Flyway의 FK로 보장한다.
@Entity
@Table(name = "purchase_orders")
class OrderEntity {
    @Id private UUID id;
    @Column(nullable = false, length = 255) private String ownerSubject;
    @Column(nullable = false) private long memberId;
    @Column(nullable = false) private long productId;
    @Column(nullable = false, length = 100) private String productName;
    @Column(nullable = false) private int quantity;
    @Column(nullable = false, precision = 19, scale = 2) private BigDecimal unitPrice;
    @Column(nullable = false, length = 3) private String currency;
    @Column(nullable = false) private Instant createdAt;
    protected OrderEntity() {}
    static OrderEntity from(StoredOrder order) {
        var entity = new OrderEntity();
        entity.id = order.id();
        entity.ownerSubject = order.ownerSubject();
        entity.memberId = order.quote().memberId();
        entity.productId = order.quote().productId();
        entity.productName = order.quote().productName();
        entity.quantity = order.quote().quantity();
        entity.unitPrice = order.quote().unitPrice();
        entity.currency = order.quote().currency();
        entity.createdAt = order.createdAt();
        return entity;
    }
    StoredOrder toDomain() {
        return new StoredOrder(id, ownerSubject,
                new OrderQuote(memberId, productId, productName, quantity, unitPrice, currency), createdAt);
    }
}
