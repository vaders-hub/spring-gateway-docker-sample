package com.example.backend.product.application.port;

import com.example.backend.product.domain.Product;
import java.util.List;
import java.util.Optional;

// application이 필요한 조회 계약. fixture/DB 구현은 infrastructure에서 교체한다.
public interface ProductRepository {
    List<Product> findAll();
    Optional<Product> findById(long id);
}
