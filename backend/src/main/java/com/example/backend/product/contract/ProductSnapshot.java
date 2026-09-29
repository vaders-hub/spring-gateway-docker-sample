package com.example.backend.product.contract;

import java.math.BigDecimal;

// 다른 feature가 알아야 할 불변 값만 공개한다. Product Entity/domain 및 HTTP DTO에 의존하지 않는다.
public record ProductSnapshot(long id, String name, BigDecimal unitPrice, String currency) {}
