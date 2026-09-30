package com.example.backend.product.api;
import com.example.backend.common.security.FeatureRoutes;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
@Configuration(proxyBeanMethods = false)
class ProductRoutes {
    @Bean
    FeatureRoutes productFeatureRoutes() { return FeatureRoutes.of("/products", "/products/**"); }
}
