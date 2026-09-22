package com.example.gateway.config.gateway;

import java.security.Principal;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class GatewayFilterConfig {

    // application.yml의 #{@principalKeyResolver}가 이 Bean을 참조한다.
    // 클라이언트 헤더 대신 Security가 검증한 Principal 이름으로 사용자별 Redis 버킷을 고른다.
    @Bean
    KeyResolver principalKeyResolver() {
        return exchange -> exchange.getPrincipal().map(Principal::getName);
    }
}
