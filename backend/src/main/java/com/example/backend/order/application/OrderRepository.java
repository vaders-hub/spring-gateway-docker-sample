package com.example.backend.order.application;

import com.example.backend.order.domain.StoredOrder;
import java.util.Optional;
import java.util.UUID;

// application이 소유한 저장/조회 port다. Entity 및 Spring Data 타입은 경계 밖으로 나오지 않는다.
public interface OrderRepository {
    StoredOrder save(StoredOrder order);
    Optional<StoredOrder> findByIdAndOwnerSubject(UUID id, String ownerSubject);
}
