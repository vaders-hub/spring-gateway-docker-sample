package com.example.backend.order.application;

import com.example.backend.common.code.ErrorCode;
import com.example.backend.common.config.ConditionalOnLearningMock;
import com.example.backend.common.exception.BusinessException;
import com.example.backend.order.domain.OrderQuote;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnLearningMock
public class OrderService {
    private final OrderCatalog catalog;
    public OrderService(OrderCatalog catalog) { this.catalog = catalog; }
    public OrderQuote preview(long memberId, long productId, int quantity) {
        if (memberId < 1 || productId < 1 || quantity < 1 || quantity > 100) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        catalog.requireMember(memberId);
        var product = catalog.product(productId);
        // 현재는 견적 계산만 한다. 저장/결제/재고 차감은 후속 단계다.
        return new OrderQuote(memberId, product.id(), product.name(), quantity, product.unitPrice(), product.currency());
    }
}
