package com.example.backend.common.config;

import java.lang.annotation.*;
import org.springframework.context.annotation.Profile;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

// DB 학습은 local/test + persistence를 함께 선택해야 한다. 운영용 활성화 정책은 별도 설계 대상이다.
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Profile("(local | test) & persistence")
@ConditionalOnProperty(name = "app.learning.persistence-enabled", havingValue = "true")
public @interface ConditionalOnLearningPersistence {}
