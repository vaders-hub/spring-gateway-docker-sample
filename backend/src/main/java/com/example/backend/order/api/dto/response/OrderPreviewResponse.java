package com.example.backend.order.api.dto.response;

import java.math.BigDecimal;
public record OrderPreviewResponse(long memberId, long productId, String productName, int quantity,
                                   BigDecimal unitPrice, BigDecimal totalPrice, String currency) {}
