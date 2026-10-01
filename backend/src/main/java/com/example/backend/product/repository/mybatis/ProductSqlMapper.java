package com.example.backend.product.repository.mybatis;

import com.example.backend.product.model.Product;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface ProductSqlMapper {
    List<Product> findAll();
    Product findById(@Param("id") long id);
}
