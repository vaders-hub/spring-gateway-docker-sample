package com.example.backend.order.repository.jpa;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface OrderJpaRepository extends JpaRepository<OrderEntity, UUID> {
    Optional<OrderEntity> findByIdAndOwnerSubject(UUID id, String ownerSubject);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update OrderEntity o set o.quantity = :quantity where o.id = :id and o.ownerSubject = :ownerSubject")
    int updateQuantityByIdAndOwnerSubject(@Param("id") UUID id, @Param("ownerSubject") String ownerSubject,
            @Param("quantity") int quantity);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from OrderEntity o where o.id = :id and o.ownerSubject = :ownerSubject")
    int deleteByIdAndOwnerSubject(@Param("id") UUID id, @Param("ownerSubject") String ownerSubject);
}
