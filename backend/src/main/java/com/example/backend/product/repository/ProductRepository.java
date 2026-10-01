package com.example.backend.product.repository;

import com.example.backend.product.model.Product;
import java.util.List;
import java.util.Optional;

// 서비스가 사용하는 조회 계약. 메모리/JPA 구현은 프로필에 따라 선택한다.
public interface ProductRepository {
    List<Product> findAll();
    Optional<Product> findById(long id);
}
