package com.example.backend.product.repository.jpa;

import com.example.backend.product.repository.ProductRepository;
import com.example.backend.product.model.Product;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@org.springframework.context.annotation.Profile("((!local & !test) | persistence) & !mybatis")
@Transactional(readOnly = true)
class ProductRepositoryJpaImpl implements ProductRepository {
    private final ProductJpaRepository repository;
    ProductRepositoryJpaImpl(ProductJpaRepository repository) { this.repository = repository; }
    // 조회 transaction 안에서 변환을 끝내므로 OSIV가 꺼져 있어도 응답에서 lazy loading이 발생하지 않는다.
    public List<Product> findAll() {
        return repository.findAll(Sort.by("id")).stream().map(ProductEntity::toDomain).toList();
    }
    public Optional<Product> findById(long id) { return repository.findById(id).map(ProductEntity::toDomain); }
}
