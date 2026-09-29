package com.example.backend.order.infrastructure;

import com.example.backend.common.config.ConditionalOnLearningFeature;
import com.example.backend.member.application.MemberService;
import com.example.backend.product.application.ProductService;
import com.example.backend.order.application.OrderCatalog;
import org.springframework.stereotype.Component;

// 다른 feature의 공개 application 서비스만 조합한다. Repository/Entity를 직접 참조하지 않는다.
@Component
@ConditionalOnLearningFeature
class LocalOrderCatalog implements OrderCatalog {
    private final MemberService members;
    private final ProductService products;
    LocalOrderCatalog(MemberService members, ProductService products) {
        this.members = members;
        this.products = products;
    }
    public void requireMember(long memberId) { members.get(memberId); }
    public ProductSnapshot product(long productId) {
        var product = products.get(productId);
        return new ProductSnapshot(product.id(), product.name(), product.unitPrice(), product.currency());
    }
}
