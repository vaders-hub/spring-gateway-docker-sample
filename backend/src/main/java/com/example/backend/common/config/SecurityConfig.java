package com.example.backend.common.config;

import com.example.backend.common.security.SecurityProblemWriter;
import com.example.platform.security.JwtAuthorities;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import com.example.backend.common.config.properties.JwtProperties;
import com.example.backend.common.config.properties.ObservabilityProperties;
import jakarta.servlet.DispatcherType;
import org.springframework.core.env.Environment;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import com.example.backend.common.security.FeatureRoutes;
import java.util.List;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;



@EnableMethodSecurity
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({JwtProperties.class, ObservabilityProperties.class})
class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            ObservabilityProperties observabilityProperties,
            SecurityProblemWriter problems, Environment environment, JwtProperties jwtProperties, List<FeatureRoutes> routes) throws Exception {
        configureStateless(http);
        configureAuthorization(http, observabilityProperties, environment, routes);
        configureJwt(http, problems, jwtProperties.usesJwks());
        return http.build();
    }

    // Bearer API의 기본 정책. Gateway와 Backend는 각 웹 스택의 API를 그대로 사용한다.
    private void configureStateless(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(AbstractHttpConfigurer::disable);
    }

    private void configureAuthorization(HttpSecurity http,
            ObservabilityProperties observabilityProperties, Environment environment, List<FeatureRoutes> routes) throws Exception {
        http
                .authorizeHttpRequests(authorize -> {
                    // 컨테이너 내부 오류 dispatch만 허용한다. 외부의 /error 직접 요청은 아래 기본 거부 정책을 따른다.
                    authorize.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll();
                    authorize.requestMatchers("/actuator/health/**", "/actuator/info").permitAll();
                    if (observabilityProperties.prometheusPublic()) {
                        authorize.requestMatchers("/actuator/prometheus").permitAll();
                    }
                    else {
                        authorize.requestMatchers("/actuator/prometheus").authenticated();
                    }
                    // 새 기능은 자신의 web 패키지에서 FeatureRoutes Bean으로 경로를 등록한다. scope는 Controller 메서드의 권한 애너테이션으로 지정한다.
                    for (var feature : routes) {
                        authorize.requestMatchers(feature.paths().toArray(String[]::new)).authenticated();
                    }
                    if (environment.getProperty("app.learning.docs-enabled", Boolean.class, false)) {
                        // Backend 로컬 문서 UI 부트스트랩용. 업무 API의 JWT 인가는 그대로 유지한다.
                        authorize.requestMatchers(HttpMethod.GET, "/v3/api-docs", "/v3/api-docs/**",
                                "/v3/api-docs.yaml", "/swagger-ui.html", "/swagger-ui/**").permitAll();
                    }
                    // 새 최상위 feature는 기본 경로 정책에 등록하기 전에는 공개되지 않는다.
                    authorize.anyRequest().denyAll();
                });
    }

    // 일반 인가 실패와 OAuth2 인증 실패는 진입점이 다르므로 같은 writer를 양쪽에 연결한다.
    private void configureJwt(HttpSecurity http, SecurityProblemWriter problems, boolean oidc) throws Exception {
        http
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(problems.authenticationEntryPoint())
                        .accessDeniedHandler(problems.accessDeniedHandler()))
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(JwtAuthorities.converter(oidc)))
                        .authenticationEntryPoint(problems.authenticationEntryPoint())
                        .accessDeniedHandler(problems.accessDeniedHandler()));
    }

}
