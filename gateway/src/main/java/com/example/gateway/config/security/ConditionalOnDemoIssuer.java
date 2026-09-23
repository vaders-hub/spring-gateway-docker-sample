package com.example.gateway.config.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;

// 토큰 발급 Controller/Service/Encoder가 반드시 같은 조건에서 함께 활성화된다.
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Profile({"local", "dev", "test"})
@ConditionalOnProperty(prefix = "app.security.demo-user", name = "enabled", havingValue = "true")
public @interface ConditionalOnDemoIssuer {
}
