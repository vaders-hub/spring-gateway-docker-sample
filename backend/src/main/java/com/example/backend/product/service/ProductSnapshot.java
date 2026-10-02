package com.example.backend.product.service;

import java.math.BigDecimal;

// 다른 기능에 필요한 불변 값만 공개한다. 내부 Product 모델·JPA Entity·HTTP DTO에 의존하지 않는다.
public record ProductSnapshot(long id, String name, BigDecimal unitPrice, String currency) {}
