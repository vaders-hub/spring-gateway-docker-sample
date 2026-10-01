package com.example.backend.order.repository.mybatis;

import com.example.backend.order.model.StoredOrder;
import com.example.backend.order.repository.OrderRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("mybatis")
class OrderRepositoryMyBatisImpl implements OrderRepository {
    private final OrderSqlMapper mapper;
    OrderRepositoryMyBatisImpl(OrderSqlMapper mapper) { this.mapper = mapper; }
    public StoredOrder save(StoredOrder order) {
        // INSERT는 즉시 실행하고 commit/rollback은 OrderServiceImpl의 트랜잭션에 맡긴다.
        if (mapper.insert(OrderRow.from(order)) != 1) { throw new IllegalStateException("Order insert affected an unexpected row count"); }
        return order;
    }
    public Optional<StoredOrder> findByIdAndOwnerSubject(UUID id, String ownerSubject) {
        return Optional.ofNullable(mapper.findByIdAndOwnerSubject(id, ownerSubject)).map(OrderRow::toDomain);
    }
}
