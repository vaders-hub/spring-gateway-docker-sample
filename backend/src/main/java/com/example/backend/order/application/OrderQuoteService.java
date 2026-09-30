package com.example.backend.order.application;

import com.example.backend.order.application.error.OrderErrorCode;
import com.example.platform.exception.BusinessException;
import com.example.backend.order.domain.OrderQuote;
import com.example.backend.order.application.port.OrderCatalog;
import org.springframework.stereotype.Service;

@Service
public class OrderQuoteService {
    private final OrderCatalog catalog;
    public OrderQuoteService(OrderCatalog catalog) { this.catalog = catalog; }
    public OrderQuote preview(long memberId, long productId, int quantity) {
        if (!catalog.memberExists(memberId)) {
            throw new BusinessException(OrderErrorCode.INVALID_MEMBER_REFERENCE);
        }
        var product = catalog.product(productId)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.INVALID_PRODUCT_REFERENCE));
        // preview 자체는 저장하지 않는다. 저장 유스케이스는 이 계산을 재사용한 뒤 별도로 INSERT한다.
        return new OrderQuote(memberId, product.id(), product.name(), quantity, product.unitPrice(), product.currency());
    }
}
