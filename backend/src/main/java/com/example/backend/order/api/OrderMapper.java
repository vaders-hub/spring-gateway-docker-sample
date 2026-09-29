package com.example.backend.order.api;

import com.example.backend.common.config.MappingConfig;
import com.example.backend.order.api.dto.OrderPreviewResponse;
import com.example.backend.order.domain.OrderQuote;
import org.mapstruct.*;

@Mapper(config = MappingConfig.class)
public interface OrderMapper {
    @Mapping(target = "totalPrice", expression = "java(source.totalPrice())")
    OrderPreviewResponse toResponse(OrderQuote source);
}
