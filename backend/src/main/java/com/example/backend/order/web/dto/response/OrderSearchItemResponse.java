package com.example.backend.order.web.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderSearchItemResponse(UUID id, long memberId, String memberName, long productId,
                                      String productName, String currentProductName, int quantity,
                                      BigDecimal unitPrice, BigDecimal totalPrice, String currency, Instant createdAt) {}
