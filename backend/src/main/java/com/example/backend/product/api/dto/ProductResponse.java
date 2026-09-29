package com.example.backend.product.api.dto;

public record ProductResponse(long id, String name, java.math.BigDecimal unitPrice, String currency) {}
