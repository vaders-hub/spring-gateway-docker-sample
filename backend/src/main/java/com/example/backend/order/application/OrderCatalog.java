package com.example.backend.order.application;

import java.math.BigDecimal;

// 다른 기능을 호출하는 adapter의 계약. 상대 기능의 DTO/저장소를 application에 노출하지 않는다.
public interface OrderCatalog {
    void requireMember(long memberId);
    ProductSnapshot product(long productId);
    record ProductSnapshot(long id, String name, BigDecimal unitPrice, String currency) {}
}
