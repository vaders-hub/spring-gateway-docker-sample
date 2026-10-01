package com.example.backend.product.service;

import com.example.backend.product.model.Product;
import java.util.List;
import java.util.Optional;

public interface ProductService {
    List<Product> list();
    Product get(long id);
    // 다른 기능에는 내부 Product 대신 이 조회 값만 공개한다.
    Optional<ProductSnapshot> findProduct(long productId);
}
