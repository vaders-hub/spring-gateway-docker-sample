package com.example.backend.config.runtime;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNoException;

class RuntimeProfileGuardTest {

    @Test
    void rejectsPublicMetricsInProduction() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("app.observability.prometheus-public", "true");
        environment.setActiveProfiles("prod");
        assertThatIllegalStateException().isThrownBy(
                () -> new RuntimeProfileGuard(environment).validateActiveProfile());
    }

    @Test
    void acceptsProductionProfile() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");

        assertThatNoException().isThrownBy(
                () -> new RuntimeProfileGuard(environment).validateActiveProfile());
    }

    @Test
    void rejectsUnsupportedProfile() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("kubernetes");

        assertThatIllegalStateException().isThrownBy(
                () -> new RuntimeProfileGuard(environment).validateActiveProfile());
    }
}
