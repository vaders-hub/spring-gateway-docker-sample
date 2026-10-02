package com.example.backend.product.repository.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

// Spring Data가 구현하는 DB 전용 인터페이스. Service가 사용하는 repository의 저장소 인터페이스와 구분한다.
interface ProductJpaRepository extends JpaRepository<ProductEntity, Long> {}
