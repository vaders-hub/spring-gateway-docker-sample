package com.example.backend.order.repository;

import com.example.backend.order.model.StoredOrder;
import java.util.Optional;
import java.util.UUID;

// Service가 사용하는 주문 저장소 인터페이스다. JPA Entity 및 Spring Data 타입은 반환하지 않는다.
public interface OrderRepository {
    StoredOrder save(StoredOrder order);
    Optional<StoredOrder> findByIdAndOwnerSubject(UUID id, String ownerSubject);
    boolean updateQuantityByIdAndOwnerSubject(UUID id, String ownerSubject, int quantity);
    boolean deleteByIdAndOwnerSubject(UUID id, String ownerSubject);
}
