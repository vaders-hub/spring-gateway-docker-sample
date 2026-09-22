package com.example.gateway.config.security;

import com.example.gateway.common.web.RequestContext;
import com.example.gateway.config.properties.SecurityProperties;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

@Configuration(proxyBeanMethods = false)
class CorsConfig {
    @Bean
    UrlBasedCorsConfigurationSource corsConfigurationSource(SecurityProperties properties) {
        // 브라우저의 허용 Origin/메서드/헤더 정책이다. JWT 인증이나 서버 간 접근 제어를 대신하지 않는다.
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(properties.cors().allowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        configuration.setAllowedHeaders(List.of(
                "Authorization",
                "Content-Type",
                RequestContext.REQUEST_ID_HEADER));
        // 브라우저 JavaScript가 응답의 requestId를 읽어 문의/로그 추적에 사용할 수 있게 한다.
        configuration.setExposedHeaders(List.of(RequestContext.REQUEST_ID_HEADER));
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(properties.cors().maxAge().toSeconds());

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

}
