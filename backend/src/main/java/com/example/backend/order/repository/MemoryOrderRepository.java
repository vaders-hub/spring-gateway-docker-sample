package com.example.backend.order.repository;
import com.example.backend.order.model.StoredOrder;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
// API 계약은 JPA와 같다. 재시작 시 소멸하며 DB 트랜잭션 검증의 대체물은 아니다.
@Repository
@Profile("(local | test) & !persistence & !mybatis")
class MemoryOrderRepository implements OrderRepository {
    private final ConcurrentHashMap<UUID, StoredOrder> orders = new ConcurrentHashMap<>();
    public StoredOrder save(StoredOrder order) { orders.put(order.id(), order); return order; }
    public Optional<StoredOrder> findByIdAndOwnerSubject(UUID id, String ownerSubject) {
        return Optional.ofNullable(orders.get(id)).filter(order -> order.ownerSubject().equals(ownerSubject));
    }
    public boolean updateQuantityByIdAndOwnerSubject(UUID id, String ownerSubject, int quantity) {
        var updated = orders.computeIfPresent(id, (key, order) ->
                order.ownerSubject().equals(ownerSubject) ? order.withQuantity(quantity) : order);
        return updated != null && updated.ownerSubject().equals(ownerSubject);
    }
    public boolean deleteByIdAndOwnerSubject(UUID id, String ownerSubject) {
        var deleted = new AtomicBoolean();
        orders.computeIfPresent(id, (key, order) -> {
            if (!order.ownerSubject().equals(ownerSubject)) return order;
            deleted.set(true);
            return null;
        });
        return deleted.get();
    }
}
