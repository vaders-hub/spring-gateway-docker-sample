package com.example.backend.common.config;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile("mybatis")
@MapperScan(basePackages = "com.example.backend", annotationClass = Mapper.class)
public class MyBatisConfig {}
