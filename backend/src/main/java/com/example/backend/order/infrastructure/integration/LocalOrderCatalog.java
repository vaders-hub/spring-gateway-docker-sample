package com.example.backend.order.infrastructure.integration;

import com.example.backend.common.config.ConditionalOnLearningFeature;
import com.example.backend.member.contract.MemberLookup;
import com.example.backend.product.contract.ProductLookup;
import com.example.backend.order.application.port.OrderCatalog;
import org.springframework.stereotype.Component;

// 주문이 요구하는 port와 상대 feature의 공개 contract를 연결하는 adapter다.
// 상대 Service/Entity/Repository를 참조하지 않아 내부 패키지 변경이 주문 업무 로직에 전파되지 않는다.
@Component
@ConditionalOnLearningFeature
class LocalOrderCatalog implements OrderCatalog {
    private final MemberLookup members;
    private final ProductLookup products;
    LocalOrderCatalog(MemberLookup members, ProductLookup products) {
        this.members = members;
        this.products = products;
    }
    public void requireMember(long memberId) { members.requireExists(memberId); }
    public ProductSnapshot product(long productId) {
        var product = products.getProduct(productId);
        // 공급자 계약을 호출자 소유 값으로 변환한다. OrderQuoteService는 product 패키지를 전혀 모른다.
        return new ProductSnapshot(product.id(), product.name(), product.unitPrice(), product.currency());
    }
}
