package com.example.backend.order.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

// JOIN 결과 전용 조회 모델. 주문 당시 값과 현재 상품명을 명시적으로 구분한다.
public record OrderSummary(UUID id, long memberId, String memberName, long productId,
                           String productName, String currentProductName, int quantity,
                           BigDecimal unitPrice, BigDecimal totalPrice, String currency, Instant createdAt) {}
