package com.example.gateway.config.runtime;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNoException;

class RuntimeProfileGuardTest {

    @Test
    void rejectsDemoTokenOverrideInProduction() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("app.security.demo-user.enabled", "true");
        environment.setActiveProfiles("prod");
        assertThatIllegalStateException().isThrownBy(
                () -> new RuntimeProfileGuard(environment).validateActiveProfile());
    }

    @Test
    void rejectsPublicMetricsInProduction() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("app.observability.prometheus-public", "true");
        environment.setActiveProfiles("prod");
        assertThatIllegalStateException().isThrownBy(
                () -> new RuntimeProfileGuard(environment).validateActiveProfile());
    }

    @Test
    void acceptsOneLifecycleProfile() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("local");

        assertThatNoException().isThrownBy(
                () -> new RuntimeProfileGuard(environment).validateActiveProfile());
    }

    @Test
    void rejectsMissingProfile() {
        assertThatIllegalStateException().isThrownBy(
                () -> new RuntimeProfileGuard(new MockEnvironment()).validateActiveProfile());
    }

    @Test
    void rejectsMultipleProfiles() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("local", "staging");

        assertThatIllegalStateException().isThrownBy(
                () -> new RuntimeProfileGuard(environment).validateActiveProfile());
    }

    @Test
    void rejectsDeploymentPlatformAsLifecycleProfile() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("kubernetes");

        assertThatIllegalStateException().isThrownBy(
                () -> new RuntimeProfileGuard(environment).validateActiveProfile());
    }
}
