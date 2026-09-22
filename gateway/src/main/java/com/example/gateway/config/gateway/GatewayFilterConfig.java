package com.example.gateway.config.gateway;

import java.security.Principal;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class GatewayFilterConfig {

    @Bean
    KeyResolver principalKeyResolver() {
        return exchange -> exchange.getPrincipal().map(Principal::getName);
    }
}
