package com.example.backend.common.config.runtime;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import static org.assertj.core.api.Assertions.*;
class LearningProfileGuardTest {
    @Test
    void publicDocsAreBlockedOutsideLocalTest() {
        for (String profile : new String[]{"dev", "staging", "prod"}) {
            for (String key : new String[]{"app.learning.docs-enabled", "springdoc.api-docs.enabled", "springdoc.swagger-ui.enabled"}) {
                var env = new MockEnvironment().withProperty(key, "true");
                env.setActiveProfiles(profile);
                assertThatIllegalStateException().isThrownBy(() -> new LearningProfileGuard(env).validate());
            }
        }
    }
    @Test
    void productionBusinessFeaturesAreAllowedWithoutPublicDocs() {
        var env = new MockEnvironment(); env.setActiveProfiles("prod");
        assertThatNoException().isThrownBy(() -> new LearningProfileGuard(env).validate());
        env.setActiveProfiles("local"); env.withProperty("app.learning.docs-enabled", "true");
        assertThatNoException().isThrownBy(() -> new LearningProfileGuard(env).validate());
    }
}
