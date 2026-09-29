package com.example.backend.product.infrastructure;

import com.example.backend.product.domain.Product;
import jakarta.persistence.*;

// DB 매핑 전용 Entity다. JPA 객체를 Controller로 노출하지 않고 domain으로 변환한다.
@Entity
@Table(name = "products")
class ProductEntity {
    @Id
    private Long id;
    @Column(name = "name", nullable = false, length = 100)
    private String name;
    @Column(name = "unit_price", nullable = false, precision = 19, scale = 2)
    private java.math.BigDecimal unitPrice;
    @Column(name = "currency", nullable = false, length = 3)
    private String currency;
    protected ProductEntity() {}
    Product toDomain() { return new Product(id, name, unitPrice, currency); }
}
