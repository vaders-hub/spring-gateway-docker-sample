package com.example.backend.product.repository.mybatis;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile("mybatis")
@MapperScan(basePackageClasses = ProductSqlMapper.class)
class ProductMyBatisConfig {}
