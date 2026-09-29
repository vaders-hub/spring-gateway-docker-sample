package com.example.backend.common.security;

import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

/** 인증 방식별 claim 차이는 이 경계에서만 변환한다. 업무 코드는 동일한 권한을 사용한다. */
public final class JwtAuthorities {
    private JwtAuthorities() { }
    public static JwtAuthenticationConverter converter(boolean oidc) {
        var authorities = new JwtGrantedAuthoritiesConverter();
        // Keycloak의 role mapping 결과만 사용한다. 클라이언트가 요청한 scope 문자열로 승격하지 않는다.
        if (oidc) authorities.setAuthoritiesClaimName("permissions");
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }
}
