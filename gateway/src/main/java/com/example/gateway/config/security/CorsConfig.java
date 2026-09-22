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
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(properties.cors().allowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        configuration.setAllowedHeaders(List.of(
                "Authorization",
                "Content-Type",
                RequestContext.REQUEST_ID_HEADER));
        configuration.setExposedHeaders(List.of(RequestContext.REQUEST_ID_HEADER));
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(properties.cors().maxAge().toSeconds());

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

}
