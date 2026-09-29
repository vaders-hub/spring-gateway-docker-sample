package com.example.backend.product.application;

import com.example.backend.common.code.ErrorCode;
import com.example.backend.common.config.ConditionalOnLearningFeature;
import com.example.backend.common.exception.BusinessException;
import com.example.backend.product.domain.Product;
import java.util.List;
import org.springframework.stereotype.Service;

// 저장소 port에만 의존하므로 fixture에서 JPA로 바꿔도 조회 업무 로직은 동일하다.
@Service
@ConditionalOnLearningFeature
public class ProductService {
    private final ProductRepository repository;
    public ProductService(ProductRepository repository) { this.repository = repository; }
    public List<Product> list() { return repository.findAll(); }
    public Product get(long id) {
        return repository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }
}
