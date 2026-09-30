package com.example.backend.order.infrastructure.integration;

import com.example.backend.member.contract.MemberLookup;
import com.example.backend.product.contract.ProductLookup;
import com.example.backend.order.application.port.OrderCatalog;
import org.springframework.stereotype.Component;

// 주문이 요구하는 port와 상대 feature의 공개 contract를 연결하는 adapter다.
// 상대 Service/Entity/Repository를 참조하지 않아 내부 패키지 변경이 주문 업무 로직에 전파되지 않는다.
@Component
class LocalOrderCatalog implements OrderCatalog {
    private final MemberLookup members;
    private final ProductLookup products;
    LocalOrderCatalog(MemberLookup members, ProductLookup products) {
        this.members = members;
        this.products = products;
    }
    public boolean memberExists(long memberId) { return members.exists(memberId); }
    public java.util.Optional<ProductSnapshot> product(long productId) {
        // 상대 contract를 호출자 port의 값으로 변환한다. HTTP 예외를 전달하지 않는다.
        return products.findProduct(productId).map(product ->
                new ProductSnapshot(product.id(), product.name(), product.unitPrice(), product.currency()));
    }
}
