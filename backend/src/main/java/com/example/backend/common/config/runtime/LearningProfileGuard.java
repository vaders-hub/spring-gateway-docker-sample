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
        boolean persistence = environment.getProperty("app.learning.persistence-enabled", Boolean.class, false);
        // 동일 port에 fixture와 JPA adapter가 동시에 등록되거나, DB 없이 DB 기능만 켜지는 설정을 차단한다.
        if (persistence != environment.acceptsProfiles(Profiles.of("persistence"))
                || (persistence && environment.getProperty("app.learning.mock-enabled", Boolean.class, false))) {
            throw new IllegalStateException("Persistence profile/flag must agree and mock must be disabled");
        }
        boolean allowed = environment.acceptsProfiles(Profiles.of("local", "test"))
                && !environment.acceptsProfiles(Profiles.of("dev", "staging", "prod"));
        if (!allowed && (environment.getProperty("app.learning.mock-enabled", Boolean.class, false)
                || environment.getProperty("app.learning.persistence-enabled", Boolean.class, false)
                || environment.getProperty("app.learning.docs-enabled", Boolean.class, false)
                || environment.getProperty("springdoc.api-docs.enabled", Boolean.class, false)
                || environment.getProperty("springdoc.swagger-ui.enabled", Boolean.class, false))) {
            throw new IllegalStateException("Learning features and API documentation require local/test profile");
        }
    }
}
