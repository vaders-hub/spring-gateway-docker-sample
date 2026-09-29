package com.example.backend.product.domain;

public record Product(long id, String name, java.math.BigDecimal unitPrice, String currency) {}
