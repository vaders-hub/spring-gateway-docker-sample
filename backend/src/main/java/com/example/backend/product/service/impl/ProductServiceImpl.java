package com.example.backend.product.service.impl;

import com.example.backend.product.service.ProductService;

import com.example.backend.product.error.ProductErrorCode;
import java.util.Optional;
import com.example.platform.exception.BusinessException;
import com.example.backend.product.model.Product;
import com.example.backend.product.repository.ProductRepository;
import com.example.backend.product.service.ProductSnapshot;
import java.util.List;
import org.springframework.stereotype.Service;

// 저장소 port에만 의존하므로 fixture에서 JPA로 바꿔도 조회 업무 로직은 동일하다.
@Service
public class ProductServiceImpl implements ProductService {
    private final ProductRepository repository;
    public ProductServiceImpl(ProductRepository repository) { this.repository = repository; }
    public List<Product> list() { return repository.findAll(); }
    // 공개 계약은 API DTO/Entity와 별도다. 내부 상품 필드가 늘어도 호출자에게 자동 전파하지 않는다.
    @Override
    public Optional<ProductSnapshot> findProduct(long productId) {
        return repository.findById(productId).map(product -> new ProductSnapshot(
                product.id(), product.name(), product.unitPrice(), product.currency()));
    }
    public Product get(long id) {
        return repository.findById(id).orElseThrow(() -> new BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND));
    }
}
