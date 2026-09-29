package com.example.backend.order.application.query;

import com.example.backend.common.code.ErrorCode;
import com.example.backend.common.config.ConditionalOnLearningFeature;
import com.example.backend.common.exception.BusinessException;
import com.example.backend.order.domain.OrderQuote;
import com.example.backend.order.application.port.OrderCatalog;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnLearningFeature
public class OrderQuoteService {
    private final OrderCatalog catalog;
    public OrderQuoteService(OrderCatalog catalog) { this.catalog = catalog; }
    public OrderQuote preview(long memberId, long productId, int quantity) {
        if (memberId < 1 || productId < 1 || quantity < 1 || quantity > 100) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        catalog.requireMember(memberId);
        var product = catalog.product(productId);
        // preview 자체는 저장하지 않는다. 저장 유스케이스는 이 계산을 재사용한 뒤 별도로 INSERT한다.
        return new OrderQuote(memberId, product.id(), product.name(), quantity, product.unitPrice(), product.currency());
    }
}
