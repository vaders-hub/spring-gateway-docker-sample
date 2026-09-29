package com.example.backend.order.application;

import com.example.backend.common.code.ErrorCode;
import com.example.backend.common.config.ConditionalOnLearningPersistence;
import com.example.backend.common.exception.BusinessException;
import com.example.backend.order.domain.StoredOrder;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnLearningPersistence
public class OrderPlacementService {
    private final OrderService quotes;
    private final OrderRepository orders;
    public OrderPlacementService(OrderService quotes, OrderRepository orders) {
        this.quotes = quotes;
        this.orders = orders;
    }
    // 회원/상품 검증 → 서버 가격 계산 → INSERT를 하나의 transaction으로 묶는다.
    // 호출이 정상 반환돼도 commit 실패 시 Controller에 결과가 전달되지 않고 전체 rollback된다.
    @Transactional
    public StoredOrder place(long memberId, long productId, int quantity, String ownerSubject) {
        if (ownerSubject == null || ownerSubject.isBlank() || ownerSubject.length() > 255) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        var quote = quotes.preview(memberId, productId, quantity);
        return orders.save(new StoredOrder(UUID.randomUUID(), ownerSubject, quote, Instant.now()));
    }
    @Transactional(readOnly = true)
    public StoredOrder get(UUID id, String ownerSubject) {
        // 다른 사용자의 주문도 404로 처리해 주문 존재 여부가 노출되지 않게 한다.
        return orders.findByIdAndOwnerSubject(id, ownerSubject)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }
}
