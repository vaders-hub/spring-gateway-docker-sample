package com.example.backend.order.application.port;

import java.math.BigDecimal;
import java.util.Optional;

// 다른 기능을 호출하는 adapter의 계약. 상대 기능의 DTO/저장소를 application에 노출하지 않는다.
public interface OrderCatalog {
    boolean memberExists(long memberId);
    Optional<ProductSnapshot> product(long productId);
    record ProductSnapshot(long id, String name, BigDecimal unitPrice, String currency) {}
}
