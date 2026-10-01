package com.example.backend.member.web;
import com.example.backend.common.security.FeatureRoutes;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
@Configuration(proxyBeanMethods = false)
class MemberRoutes {
    @Bean
    FeatureRoutes memberFeatureRoutes() { return FeatureRoutes.of("/members", "/members/**"); }
}
