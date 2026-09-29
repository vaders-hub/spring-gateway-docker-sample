package com.example.backend.order.api;

import com.example.backend.common.config.MappingConfig;
import com.example.backend.order.api.dto.OrderPreviewResponse;
import com.example.backend.order.domain.OrderQuote;
import org.mapstruct.*;

@Mapper(config = MappingConfig.class)
public interface OrderMapper {
    @Mapping(target = "totalPrice", expression = "java(source.totalPrice())")
    OrderPreviewResponse toResponse(OrderQuote source);

    // 내부 인가용 ownerSubject는 응답 DTO에 노출하지 않는다. 중첩 quote에도 위 매핑이 적용된다.
    com.example.backend.order.api.dto.OrderResponse toResponse(com.example.backend.order.domain.StoredOrder source);
}
