package com.example.gateway.common.config;

import com.example.gateway.common.security.SecurityProblemWriter;
import com.example.gateway.common.security.JwtAuthorities;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import com.example.gateway.common.config.properties.ObservabilityProperties;
import com.example.gateway.common.config.properties.SecurityProperties;
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
            SecurityProblemWriter problems, SecurityProperties jwtProperties) {
        configureStateless(http);
        configureAuthorization(http, observabilityProperties);
        configureJwt(http, problems, jwtProperties.jwt().usesJwks());
        return http.build();
    }

    // Bearer API의 기본 정책. Gateway와 Backend는 각 웹 스택의 API를 그대로 사용한다.
    private void configureStateless(ServerHttpSecurity http) {
        http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .cors(Customizer.withDefaults())
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                .requestCache(cache -> cache.requestCache(NoOpServerRequestCache.getInstance()));
    }

    private void configureAuthorization(ServerHttpSecurity http,
            ObservabilityProperties observabilityProperties) {
        http
                .authorizeExchange(authorize -> {
                    authorize.pathMatchers(HttpMethod.POST, "/auth/login", "/auth/token").permitAll();
                    authorize.pathMatchers(HttpMethod.POST, "/auth/logout").authenticated();
                    authorize.pathMatchers("/actuator/health/**", "/actuator/info").permitAll();
                    if (observabilityProperties.prometheusPublic()) {
                        authorize.pathMatchers("/actuator/prometheus").permitAll();
                    }
                    else {
                        authorize.pathMatchers("/actuator/prometheus").authenticated();
                    }
                    // 업무 토큰으로 Backend 관리/오류 경로에 우회 접근하지 못하게 scope 허용보다 먼저 거부한다.
                    authorize.pathMatchers("/api/actuator", "/api/actuator/**", "/api/error", "/api/error/**")
                            .denyAll();
                    // read/write scope로 HTTP 동작을 구분한다. 토큰은 있으나 scope가 부족하면 403이다.
                    requireScope(authorize, "api.read", "/api/**", HttpMethod.GET, HttpMethod.HEAD);
                    requireScope(authorize, "api.write", "/api/**",
                            HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE);
                    authorize.pathMatchers("/api/**").denyAll();
                    authorize.anyExchange().authenticated();
                });
    }

    // 일반 인가 실패와 OAuth2 인증 실패는 진입점이 다르므로 같은 writer를 양쪽에 연결한다.
    private void configureJwt(ServerHttpSecurity http, SecurityProblemWriter problems, boolean oidc) {
        http
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(problems.authenticationEntryPoint())
                        .accessDeniedHandler(problems.accessDeniedHandler()))
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(new ReactiveJwtAuthenticationConverterAdapter(
                                JwtAuthorities.converter(oidc))))
                        .authenticationEntryPoint(problems.authenticationEntryPoint())
                        .accessDeniedHandler(problems.accessDeniedHandler()));
    }

    private void requireScope(ServerHttpSecurity.AuthorizeExchangeSpec authorize,
            String scope, String path, HttpMethod... methods) {
        for (HttpMethod method : methods) {
            authorize.pathMatchers(method, path).access(hasScope(scope));
        }
    }
}
