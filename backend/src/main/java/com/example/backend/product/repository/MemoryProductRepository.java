package com.example.backend.product.repository;

import com.example.backend.product.model.Product;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

// 고정된 불변 학습 데이터다. 사용자 세션/주문 상태를 메모리에 저장하지 않는다.
@Repository
@org.springframework.context.annotation.Profile("(local | test) & !persistence & !mybatis")
class MemoryProductRepository implements ProductRepository {
    private static final List<Product> DATA = List.of(new Product(1, "Keyboard", new java.math.BigDecimal("50000"), "KRW"), new Product(2, "Mouse", new java.math.BigDecimal("20000"), "KRW"));
    public List<Product> findAll() { return DATA; }
    public Optional<Product> findById(long id) { return DATA.stream().filter(item -> item.id() == id).findFirst(); }
}
