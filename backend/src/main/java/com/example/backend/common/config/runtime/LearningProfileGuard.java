package com.example.backend.common.config.runtime;

import jakarta.annotation.PostConstruct;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

@Component
class LearningProfileGuard {
    private final Environment environment;
    LearningProfileGuard(Environment environment) { this.environment = environment; }
    @PostConstruct
    void validate() {
        boolean allowed = environment.acceptsProfiles(Profiles.of("local", "test"))
                && !environment.acceptsProfiles(Profiles.of("dev", "staging", "prod"));
        if (!allowed && (environment.getProperty("app.learning.mock-enabled", Boolean.class, false)
                || environment.getProperty("app.learning.docs-enabled", Boolean.class, false)
                || environment.getProperty("springdoc.api-docs.enabled", Boolean.class, false)
                || environment.getProperty("springdoc.swagger-ui.enabled", Boolean.class, false))) {
            throw new IllegalStateException("Learning mock and API documentation require local/test profile");
        }
    }
}
