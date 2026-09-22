package com.example.backend.config.runtime;

import jakarta.annotation.PostConstruct;
import java.util.Arrays;
import java.util.Set;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
class RuntimeProfileGuard {

    private static final Set<String> ALLOWED_PROFILES = Set.of(
            "local", "dev", "test", "staging", "prod");

    private final Environment environment;

    public RuntimeProfileGuard(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    void validateActiveProfile() {
        String[] activeProfiles = environment.getActiveProfiles();
        if (activeProfiles.length != 1
                || !ALLOWED_PROFILES.contains(activeProfiles[0])) {
            throw new IllegalStateException(
                    "Exactly one supported profile must be active: "
                            + ALLOWED_PROFILES
                            + "; active="
                            + Arrays.toString(activeProfiles));
        }

        String profile = activeProfiles[0];
        if (!Set.of("local", "test").contains(profile)
                && environment.getProperty("app.observability.prometheus-public", Boolean.class, false)) {
            throw new IllegalStateException(
                    "Public Prometheus access is permitted only in local/test profiles");
        }

    }
}
