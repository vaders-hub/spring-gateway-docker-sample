package com.example.gateway.config.security;

import com.example.gateway.common.error.SecurityProblemWriter;
import com.example.gateway.config.properties.ObservabilityProperties;
import com.example.gateway.config.properties.SecurityProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.security.web.server.savedrequest.NoOpServerRequestCache;

import static org.springframework.security.oauth2.core.authorization.OAuth2ReactiveAuthorizationManagers.hasScope;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({SecurityProperties.class, ObservabilityProperties.class})
class SecurityConfig {

    @Bean
    SecurityWebFilterChain securityWebFilterChain(
            ServerHttpSecurity http,
            ObservabilityProperties observabilityProperties,
            SecurityProblemWriter problems) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .cors(Customizer.withDefaults())
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                .requestCache(cache -> cache.requestCache(NoOpServerRequestCache.getInstance()))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(problems.authenticationEntryPoint())
                        .accessDeniedHandler(problems.accessDeniedHandler()))
                .authorizeExchange(authorize -> {
                    authorize.pathMatchers(HttpMethod.POST, "/auth/token").permitAll();
                    authorize.pathMatchers("/actuator/health/**", "/actuator/info").permitAll();
                    if (observabilityProperties.prometheusPublic()) {
                        authorize.pathMatchers("/actuator/prometheus").permitAll();
                    }
                    else {
                        authorize.pathMatchers("/actuator/prometheus").authenticated();
                    }
                    authorize.pathMatchers(HttpMethod.GET, "/api/**").access(hasScope("api.read"));
                    authorize.pathMatchers(HttpMethod.HEAD, "/api/**").access(hasScope("api.read"));
                    authorize.pathMatchers(HttpMethod.POST, "/api/**").access(hasScope("api.write"));
                    authorize.pathMatchers(HttpMethod.PUT, "/api/**").access(hasScope("api.write"));
                    authorize.pathMatchers(HttpMethod.PATCH, "/api/**").access(hasScope("api.write"));
                    authorize.pathMatchers(HttpMethod.DELETE, "/api/**").access(hasScope("api.write"));
                    authorize.pathMatchers("/api/**").denyAll();
                    authorize.anyExchange().authenticated();
                })
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(Customizer.withDefaults())
                        .authenticationEntryPoint(problems.authenticationEntryPoint())
                        .accessDeniedHandler(problems.accessDeniedHandler()))
                .build();
    }

}
