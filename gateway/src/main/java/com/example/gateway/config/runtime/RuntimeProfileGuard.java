package com.example.gateway.config.runtime;

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

    // 4·6단계: Bean 초기화 시 잘못된 환경 조합을 발견하면 시작을 실패시킨다.
    // local,prod 동시 활성화 장애 실습이 여기서 차단된다. 배포 플랫폼은 별도 설정값이다.
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
        if (Set.of("staging", "prod").contains(profile)
                && environment.getProperty("app.security.demo-user.enabled", Boolean.class, false)) {
            throw new IllegalStateException(
                    "Demo token issuance must be disabled in staging/prod");
        }

    }
}
