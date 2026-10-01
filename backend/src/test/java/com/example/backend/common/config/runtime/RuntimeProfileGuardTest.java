package com.example.backend.common.config.runtime;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNoException;

class RuntimeProfileGuardTest {
    @Test
    void permitsMyBatisWithOneLifecycleAndOidc() {
        var env = new MockEnvironment();
        for (String lifecycle : java.util.List.of("local", "test", "dev", "staging", "prod")) {
            env.setActiveProfiles(lifecycle, "mybatis", "oidc");
            assertThatNoException().isThrownBy(() -> new RuntimeProfileGuard(env).validateActiveProfile());
        }
    }

    @Test
    void rejectsMyBatisWithoutLifecycleOrAlongsideJpaSelection() {
        var env = new MockEnvironment();
        env.setActiveProfiles("mybatis");
        assertThatIllegalStateException().isThrownBy(() -> new RuntimeProfileGuard(env).validateActiveProfile());
        env.setActiveProfiles("test", "persistence", "mybatis");
        assertThatIllegalStateException().isThrownBy(() -> new RuntimeProfileGuard(env).validateActiveProfile());
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
    void acceptsProductionProfile() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");

        assertThatNoException().isThrownBy(
                () -> new RuntimeProfileGuard(environment).validateActiveProfile());
    }

    @Test
    void permitsOnlyLocalTestPersistenceCombination() {
        for (String lifecycle : new String[]{"local", "test"}) {
            var env = new MockEnvironment();
            env.setActiveProfiles("persistence", lifecycle);
            assertThatNoException().isThrownBy(() -> new RuntimeProfileGuard(env).validateActiveProfile());
        }
        for (String[] profiles : new String[][]{{"local", "prod"}, {"prod", "persistence"},
                {"persistence"}, {"local", "other"}, {}}) {
            var env = new MockEnvironment();
            env.setActiveProfiles(profiles);
            assertThatIllegalStateException().isThrownBy(() -> new RuntimeProfileGuard(env).validateActiveProfile());
        }
    }
    @Test
    void rejectsUnsupportedProfile() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("kubernetes");

        assertThatIllegalStateException().isThrownBy(
                () -> new RuntimeProfileGuard(environment).validateActiveProfile());
    }
    @Test
    void permitsOidcAlongsideOneLifecycleButNotAlone() {
        var env = new MockEnvironment();
        env.setActiveProfiles("local", "oidc");
        assertThatNoException().isThrownBy(() -> new RuntimeProfileGuard(env).validateActiveProfile());
        env.setActiveProfiles("oidc");
        assertThatIllegalStateException().isThrownBy(() -> new RuntimeProfileGuard(env).validateActiveProfile());
    }
}
