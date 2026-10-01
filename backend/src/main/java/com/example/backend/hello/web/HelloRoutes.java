package com.example.backend.hello.web;
import com.example.backend.common.security.FeatureRoutes;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
@Configuration(proxyBeanMethods = false)
class HelloRoutes {
    @Bean
    FeatureRoutes helloFeatureRoutes() { return FeatureRoutes.of("/hello", "/echo"); }
}
