package com.example.backend.product.application;

import com.example.backend.common.code.ErrorCode;
import com.example.backend.common.config.ConditionalOnLearningFeature;
import com.example.backend.common.exception.BusinessException;
import com.example.backend.product.domain.Product;
import com.example.backend.product.application.port.ProductRepository;
import com.example.backend.product.contract.ProductLookup;
import com.example.backend.product.contract.ProductSnapshot;
import java.util.List;
import org.springframework.stereotype.Service;

// 저장소 port에만 의존하므로 fixture에서 JPA로 바꿔도 조회 업무 로직은 동일하다.
@Service
@ConditionalOnLearningFeature
public class ProductService implements ProductLookup {
    private final ProductRepository repository;
    public ProductService(ProductRepository repository) { this.repository = repository; }
    public List<Product> list() { return repository.findAll(); }
    // 공개 계약은 API DTO/Entity와 별도다. 내부 상품 필드가 늘어도 호출자에게 자동 전파하지 않는다.
    @Override
    public ProductSnapshot getProduct(long productId) {
        var product = get(productId);
        return new ProductSnapshot(
                product.id(), product.name(), product.unitPrice(), product.currency());
    }
    public Product get(long id) {
        return repository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }
}
