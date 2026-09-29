package com.example.backend.product.contract;

/** 상품 feature의 공개 조회 경계. HTTP 호출이 아닌 동일 애플리케이션 안의 Java 인터페이스다. */
public interface ProductLookup {
    ProductSnapshot getProduct(long productId);
}
