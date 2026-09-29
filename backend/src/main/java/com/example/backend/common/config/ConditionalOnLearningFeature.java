package com.example.backend.common.config;

import java.lang.annotation.*;
import org.springframework.context.annotation.Profile;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;

// API와 업무 로직은 공통으로 사용하고, 실제 저장소 구현만 fixture/JPA 중에서 선택한다.
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Profile({"local", "test"})
@ConditionalOnExpression("${app.learning.mock-enabled:false} or ${app.learning.persistence-enabled:false}")
public @interface ConditionalOnLearningFeature {}
