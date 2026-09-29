package com.example.backend.order.application.query;

import com.example.backend.common.code.ErrorCode;
import com.example.backend.common.config.ConditionalOnLearningPersistence;
import com.example.backend.common.exception.BusinessException;
import com.example.backend.order.application.port.OrderRepository;
import com.example.backend.order.domain.StoredOrder;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 조회와 변경의 책임을 나눈다. 별도 조회 DB/이벤트 동기화를 도입하는 CQRS 시스템은 아니다.
@Service
@ConditionalOnLearningPersistence
public class OrderQueryService {
    private final OrderRepository orders;
    public OrderQueryService(OrderRepository orders) { this.orders = orders; }
    @Transactional(readOnly = true)
    public StoredOrder get(UUID id, String ownerSubject) {
        // 다른 사용자의 주문도 404로 처리한다. 읽기 서비스를 나눠도 소유권 검증은 유지해야 한다.
        return orders.findByIdAndOwnerSubject(id, ownerSubject)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }
}
