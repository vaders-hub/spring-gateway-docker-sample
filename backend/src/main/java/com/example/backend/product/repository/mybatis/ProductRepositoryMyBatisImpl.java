package com.example.backend.product.repository.mybatis;

import com.example.backend.product.model.Product;
import com.example.backend.product.repository.ProductRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Profile("mybatis")
@Transactional(readOnly = true)
class ProductRepositoryMyBatisImpl implements ProductRepository {
    private final ProductSqlMapper mapper;
    ProductRepositoryMyBatisImpl(ProductSqlMapper mapper) { this.mapper = mapper; }
    public List<Product> findAll() { return mapper.findAll(); }
    public Optional<Product> findById(long id) { return Optional.ofNullable(mapper.findById(id)); }
}
