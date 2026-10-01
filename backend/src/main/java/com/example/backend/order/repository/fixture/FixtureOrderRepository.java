package com.example.backend.order.repository.fixture;
import com.example.backend.order.repository.OrderRepository;
import com.example.backend.order.model.StoredOrder;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
// API 계약은 JPA와 같다. 재시작 시 소멸하며 DB 트랜잭션 검증의 대체물은 아니다.
@Repository
@Profile("(local | test) & !persistence")
class FixtureOrderRepository implements OrderRepository {
    private final ConcurrentHashMap<UUID, StoredOrder> orders = new ConcurrentHashMap<>();
    public StoredOrder save(StoredOrder order) { orders.put(order.id(), order); return order; }
    public Optional<StoredOrder> findByIdAndOwnerSubject(UUID id, String ownerSubject) {
        return Optional.ofNullable(orders.get(id)).filter(order -> order.ownerSubject().equals(ownerSubject));
    }
}
