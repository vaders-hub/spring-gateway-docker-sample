package com.example.backend.config.security;

import com.example.backend.common.error.SecurityProblemWriter;
import com.example.backend.config.properties.JwtProperties;
import com.example.backend.config.properties.ObservabilityProperties;
import jakarta.servlet.DispatcherType;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

import static org.springframework.security.oauth2.core.authorization.OAuth2AuthorizationManagers.hasScope;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({JwtProperties.class, ObservabilityProperties.class})
class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            ObservabilityProperties observabilityProperties,
            SecurityProblemWriter problems) throws Exception {
        // 1단계: 내부 네트워크나 X-Gateway-User만 신뢰하지 않고 Backend도 Bearer JWT를 검증한다.
        // 인가 경로는 Gateway가 /api를 제거한 이후의 /hello, /echo 기준이다.
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(AbstractHttpConfigurer::disable)
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(problems.authenticationEntryPoint())
                        .accessDeniedHandler(problems.accessDeniedHandler()))
                .authorizeHttpRequests(authorize -> {
                    // 컨테이너 내부 오류 dispatch만 허용한다. 외부의 /error 직접 요청은 기존 인증 정책을 따른다.
                    authorize.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll();
                    authorize.requestMatchers("/actuator/health/**", "/actuator/info").permitAll();
                    if (observabilityProperties.prometheusPublic()) {
                        authorize.requestMatchers("/actuator/prometheus").permitAll();
                    }
                    else {
                        authorize.requestMatchers("/actuator/prometheus").authenticated();
                    }
                    authorize.requestMatchers(HttpMethod.GET, "/hello").access(hasScope("api.read"));
                    authorize.requestMatchers(HttpMethod.HEAD, "/hello").access(hasScope("api.read"));
                    authorize.requestMatchers(HttpMethod.POST, "/echo").access(hasScope("api.write"));
                    authorize.anyRequest().authenticated();
                })
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(Customizer.withDefaults())
                        .authenticationEntryPoint(problems.authenticationEntryPoint())
                        .accessDeniedHandler(problems.accessDeniedHandler()))
                .build();
    }

}
