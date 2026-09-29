package com.example.backend.product.api;

import com.example.backend.common.config.MappingConfig;
import com.example.backend.product.domain.Product;
import com.example.backend.product.api.dto.ProductResponse;
import java.util.List;
import org.mapstruct.Mapper;

@Mapper(config = MappingConfig.class)
public interface ProductMapper {
    ProductResponse toResponse(Product source);
    List<ProductResponse> toResponses(List<Product> source);
}
