package com.example.backend.order.service.impl;

import com.example.backend.member.service.MemberService;
import com.example.backend.product.service.ProductService;
import com.example.backend.order.error.OrderErrorCode;
import com.example.backend.order.model.OrderQuote;
import com.example.backend.order.model.StoredOrder;
import com.example.backend.order.repository.OrderRepository;
import com.example.backend.order.service.OrderService;
import com.example.platform.code.CommonErrorCode;
import com.example.platform.exception.BusinessException;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderServiceImpl implements OrderService {
    private final MemberService members;
    private final ProductService products;
    private final OrderRepository orders;
    private final Clock clock;

    public OrderServiceImpl(MemberService members, ProductService products, OrderRepository orders, Clock clock) {
        this.members = members;
        this.products = products;
        this.orders = orders;
        this.clock = clock;
    }

    @Override
    public OrderQuote preview(long memberId, long productId, int quantity) {
        if (!members.exists(memberId)) {
            throw new BusinessException(OrderErrorCode.INVALID_MEMBER_REFERENCE);
        }
        var product = products.findProduct(productId)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.INVALID_PRODUCT_REFERENCE));
        // 상품 단건 조회의 404를 전달하지 않고 주문 참조 오류 422를 유지한다.
        return new OrderQuote(memberId, product.id(), product.name(), quantity, product.unitPrice(), product.currency());
    }

    @Override
    @Transactional
    public StoredOrder place(long memberId, long productId, int quantity, String ownerSubject) {
        if (ownerSubject == null || ownerSubject.isBlank() || ownerSubject.length() > 255) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        }
        // 외부에서 호출한 place의 트랜잭션 안에서 검증·계산·저장을 수행한다.
        // preview 내부 호출에 별도 트랜잭션 advice가 적용된다고 가정하지 않는다.
        var quote = preview(memberId, productId, quantity);
        return orders.save(new StoredOrder(UUID.randomUUID(), ownerSubject, quote, clock.instant()));
    }

    @Override
    @Transactional(readOnly = true)
    public StoredOrder get(UUID id, String ownerSubject) {
        // 다른 사용자의 주문도 404로 처리하며 저장소 조회 자체에 소유자 조건을 적용한다.
        return orders.findByIdAndOwnerSubject(id, ownerSubject)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));
    }
}
