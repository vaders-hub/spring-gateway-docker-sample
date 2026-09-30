package com.example.backend.common.security;
import java.util.List;
// 기능은 공개할 경로만 소유한다. 인증은 필터 체인, scope/업무 권한은 메서드가 결정한다.
public record FeatureRoutes(List<String> paths) {
    public FeatureRoutes { paths = List.copyOf(paths); }
    public static FeatureRoutes of(String... paths) { return new FeatureRoutes(List.of(paths)); }
}
