package com.example.backend.common.config.runtime;
import jakarta.annotation.PostConstruct;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

// 업무 기능은 모든 환경에서 제공한다. 학습용 공개 문서만 운영에서 금지한다.
@Component
class LearningProfileGuard {
    private final Environment environment;
    LearningProfileGuard(Environment environment) { this.environment = environment; }
    @PostConstruct
    void validate() {
        boolean local = environment.acceptsProfiles(Profiles.of("local", "test"))
                && !environment.acceptsProfiles(Profiles.of("dev", "staging", "prod"));
        if (!local && (environment.getProperty("app.learning.docs-enabled", Boolean.class, false)
                || environment.getProperty("springdoc.api-docs.enabled", Boolean.class, false)
                || environment.getProperty("springdoc.swagger-ui.enabled", Boolean.class, false))) {
            throw new IllegalStateException("Public API documentation requires local/test profile");
        }
    }
}
