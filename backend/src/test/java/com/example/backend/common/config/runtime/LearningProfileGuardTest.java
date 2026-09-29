package com.example.backend.common.config.runtime;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import static org.assertj.core.api.Assertions.*;

class LearningProfileGuardTest {
    @Test
    void everyLearningSwitchIsBlockedOutsideLocalTest() {
        for (String profile : new String[]{"dev", "staging", "prod"}) {
            for (String key : new String[]{"app.learning.mock-enabled", "app.learning.docs-enabled",
                    "springdoc.api-docs.enabled", "springdoc.swagger-ui.enabled"}) {
                var env = new MockEnvironment().withProperty(key, "true");
                env.setActiveProfiles(profile);
                assertThatIllegalStateException().isThrownBy(() -> new LearningProfileGuard(env).validate());
            }
        }
    }
    @Test
    void localLearningAndProductionDefaultsAreAccepted() {
        var env = new MockEnvironment().withProperty("app.learning.mock-enabled", "true");
        env.setActiveProfiles("local");
        assertThatNoException().isThrownBy(() -> new LearningProfileGuard(env).validate());
        var prod = new MockEnvironment();
        prod.setActiveProfiles("prod");
        assertThatNoException().isThrownBy(() -> new LearningProfileGuard(prod).validate());
    }
}
