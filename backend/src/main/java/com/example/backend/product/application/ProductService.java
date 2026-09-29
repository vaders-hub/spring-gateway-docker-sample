package com.example.backend.product.application;

import com.example.backend.common.code.ErrorCode;
import com.example.backend.common.config.ConditionalOnLearningMock;
import com.example.backend.common.exception.BusinessException;
import com.example.backend.product.domain.Product;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnLearningMock
public class ProductService {
    private final ProductRepository repository;
    public ProductService(ProductRepository repository) { this.repository = repository; }
    public List<Product> list() { return repository.findAll(); }
    public Product get(long id) {
        return repository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }
}
