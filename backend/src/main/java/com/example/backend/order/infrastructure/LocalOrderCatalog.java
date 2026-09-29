package com.example.backend.order.infrastructure;

import com.example.backend.common.config.ConditionalOnLearningMock;
import com.example.backend.member.application.MemberService;
import com.example.backend.product.application.ProductService;
import com.example.backend.order.application.OrderCatalog;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnLearningMock
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
