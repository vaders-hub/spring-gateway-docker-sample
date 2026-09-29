package com.example.backend.common.config.runtime;

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
        // lifecycle은 여전히 정확히 하나다. Backend 학습 DB용 persistence만 추가 profile로 허용한다.
        var lifecycle = Arrays.stream(activeProfiles).filter(ALLOWED_PROFILES::contains).toList();
        boolean unsupported = Arrays.stream(activeProfiles)
                .anyMatch(profile -> !ALLOWED_PROFILES.contains(profile) && !profile.equals("persistence"));
        if (lifecycle.size() != 1 || unsupported
                || (Arrays.asList(activeProfiles).contains("persistence")
                    && !Set.of("local", "test").contains(lifecycle.getFirst()))) {
            throw new IllegalStateException(
                    "Exactly one lifecycle profile must be active; persistence is optional in local/test: "
                            + ALLOWED_PROFILES
                            + "; active="
                            + Arrays.toString(activeProfiles));
        }

        String profile = lifecycle.getFirst();
        if (!Set.of("local", "test").contains(profile)
                && environment.getProperty("app.observability.prometheus-public", Boolean.class, false)) {
            throw new IllegalStateException(
                    "Public Prometheus access is permitted only in local/test profiles");
        }

    }
}
