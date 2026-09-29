package com.example.backend.common.config;

import java.lang.annotation.*;
import org.springframework.context.annotation.Profile;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

// 학습 fixture는 명시적으로 켠 local/test 환경에서만 생성한다.
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Profile({"local", "test"})
@ConditionalOnProperty(name = "app.learning.mock-enabled", havingValue = "true")
public @interface ConditionalOnLearningMock {}
