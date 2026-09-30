package com.example.backend.order.api;
import com.example.backend.common.security.FeatureRoutes;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
@Configuration(proxyBeanMethods = false)
class OrderRoutes {
    @Bean
    FeatureRoutes orderFeatureRoutes() { return FeatureRoutes.of("/orders", "/orders/**"); }
}
