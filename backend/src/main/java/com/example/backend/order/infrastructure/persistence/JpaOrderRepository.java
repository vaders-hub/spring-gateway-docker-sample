package com.example.backend.order.infrastructure.persistence;

import com.example.backend.common.config.ConditionalOnLearningPersistence;
import com.example.backend.order.application.port.OrderRepository;
import com.example.backend.order.domain.StoredOrder;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnLearningPersistence
class JpaOrderRepository implements OrderRepository {
    private final OrderJpaRepository repository;
    JpaOrderRepository(OrderJpaRepository repository) { this.repository = repository; }
    public StoredOrder save(StoredOrder order) {
        // UUID는 application에서 이미 생성했다. flush가 SQL을 실행해도 최종 commit은 서비스가 담당한다.
        return repository.saveAndFlush(OrderEntity.from(order)).toDomain();
    }
    public Optional<StoredOrder> findByIdAndOwnerSubject(UUID id, String ownerSubject) {
        return repository.findByIdAndOwnerSubject(id, ownerSubject).map(OrderEntity::toDomain);
    }
}
