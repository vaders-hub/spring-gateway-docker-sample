package com.example.backend.product.model;

public record Product(long id, String name, java.math.BigDecimal unitPrice, String currency) {}
