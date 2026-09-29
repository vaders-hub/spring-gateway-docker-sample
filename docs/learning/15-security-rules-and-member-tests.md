# 15. 보안 설정 간소화와 member 40x 실습

> 인증 모드 구분: 이 문서의 HS256/Redis 로그인은 기본 데모 Compose용이다.
> Keycloak 레퍼런스 구성은 [16단계](16-keycloak-and-method-security.md)를 사용한다.
> 현재 Backend는 feature 경로 기본 정책 + 메서드 권한을 사용하며 `/members/admin`은
> `@RequireMemberAdmin`에서 검사한다. 기본 denyAll과 200/401/403 응답 계약은 유지한다.


## 설정 분리 기준

Gateway(WebFlux)와 Backend(MVC)의 `SecurityConfig`는 각 스택을 유지하면서 아래 순서로 설정을 조립합니다.

```java
configureStateless(http);
configureAuthorization(http, observabilityProperties /* Backend는 environment도 전달 */);
configureJwt(http, problems, /* JWKS 방식 여부 */ oidc);
return http.build();
```

| 메서드 | 책임 |
|---|---|
| configureStateless | 세션/요청 캐시/기본 로그인 방식과 CORS/CSRF 설정 |
| configureAuthorization | 공개 URL → 구체적인 제한 → 일반 업무 URL → 기본 정책 |
| configureJwt | JWT 인증과 인증·인가 실패의 공통 응답 writer 연결 |
| requireScope | 반복되는 메서드·경로·scope 등록만 공통화 |

Gateway는 `/api/**`의 읽기/쓰기 메서드를 묶습니다. Backend는 feature 경로와 HTTP 메서드의 기본 정책을 등록하고 세부 권한을 메서드에서 검사합니다.
두 서비스의 Security API를 하나의 추상 부모 클래스나 util로 감싸지 않습니다.
규칙이 더 커질 때 `GatewayAuthorization`/`BackendAuthorization`으로 이동할 수 있으며, 현재는 private 메서드로 충분합니다.
회원/주문 소유권 같은 업무 권한은 각 feature의 application에서 검사하는 기존 원칙을 유지합니다.

## member API

| Gateway URL (GET) | 필요 scope | 동작 |
|---|---|---|
| `/api/members` | api.read | 회원 목록 |
| `/api/members/{id}` | api.read | 양의 정수 ID 회원 조회 |
| `/api/members/admin` | api.read + member.admin | 관리 권한이 필요한 회원 목록 |

Backend 직접 URL은 `/api`를 제외합니다. 기존 local/test 목업 또는 persistence 조건에서만 member Controller가 활성화됩니다.
관리 경로는 현재 일반 목록과 같은 DTO/서비스를 재사용하는 권한 학습용 읽기 API입니다.
일반/관리 handler를 분리하고 공통 응답 매핑과 Service를 재사용합니다. 관리 기능/정보가 달라지면 별도 DTO·유스케이스로 분리합니다.

현재 `/members/admin`의 추가 권한은 `@RequireMemberAdmin` 메서드 보안으로 검사합니다.
SecurityConfig의 feature 기본 읽기 정책을 통과한 뒤 api.read + member.admin을 모두 요구합니다.
URL 규칙 자체는 여전히 먼저 일치하는 규칙이 적용되므로 Gateway 관리 경로 차단 같은 구체적 제한은 일반 허용보다 먼저 선언합니다.

기존 데모 로그인은 `api.read api.write`만 발급합니다. 일반 로그인으로 관리 경로를 호출하면 403이 정상입니다.
테스트를 위해 공개 로그인 요청에 임의 scope/role을 넣어 권한을 올리는 기능은 추가하지 않았습니다.
관리자 허용(200)과 각각의 scope 누락(403)은 자동 테스트의 격리된 발급기로 검증합니다.

## 빠른 실습

소스 반영 후 양쪽 이미지를 다시 빌드/기동합니다. 사용 중인 PostgreSQL/관측 overlay를 유지합니다.
저장소 루트의 WSL Bash에서 실행합니다.

```bash
bash scripts/check-member-security.sh
# kind 포트포워딩 등 다른 학습 진입점도 local-api.sh의 허용 범위 내에서 지정 가능
# bash scripts/check-member-security.sh http://localhost:8888
```

이 스크립트는 자체 로그인 토큰을 만들고 종료 시 폐기하며 JWT/비밀번호를 출력하지 않습니다.
회원 데이터는 조회만 하고 Redis/DB/앱을 재시작하지 않습니다. 429가 섞이지 않도록 테스트 중 다른 부하 호출은 멈춥니다.

직접 실행하려면:

```bash
source scripts/local-api.sh
lab_login
lab_api GET /api/members                 # 200
lab_api GET /api/members/admin           # 403
lab_api GET /api/members/not-a-number    # 400
lab_api GET /api/members/0               # 400
lab_api GET /api/members/9223372036854775807  # 404
lab_logout
```

수동 호출도 약 1초 간격을 두세요. `/members/{id}`의 0·음수는 `@Positive`로 거부합니다.
문자 ID는 Spring의 타입 변환에서 400입니다. 양수지만 존재하지 않는 ID는 서비스에서 404입니다.

## 응답을 구분하는 기준

| 상황 | 응답 | 검사 위치 |
|---|---|---|
| 토큰 없음/서명 변조/만료/issuer·audience 불일치/로그아웃 토큰 | 401 UNAUTHORIZED | Gateway, Backend 각각의 JWT 인증 |
| 일반 토큰으로 관리 목록 접근 | 403 ACCESS_DENIED | Gateway의 api.read 통과 후 Backend의 member.admin 검사 |
| api.read 없는 토큰으로 회원 조회 | 403 ACCESS_DENIED | Gateway 또는 Backend scope 검사 |
| 형식이 잘못됐거나 0·음수인 ID | 400 INVALID_REQUEST | 인증·인가 통과 후 Backend 입력 검증 |
| 존재하지 않는 양수 ID | 404 NOT_FOUND | Backend member application 조회 |
| 빠른 반복 호출 | 429 TOO_MANY_REQUESTS | 기존 Gateway Redis rate limit |
| 활성 토큰 저장소 장애 | 503 SERVICE_UNAVAILABLE | JWT 활성 상태 검사 |

400·404는 JWT 오류가 아닙니다. 인증이 안 되면 잘못된 ID도 먼저 401, scope가 부족하면 먼저 403입니다.
HTTP/JSON 테스트는 `requestId`, Problem Details 형식 및 민감값 미노출도 검사합니다.
관리 경로의 권한 판단은 Backend가 담당하므로 향후 Gateway 외의 내부 호출에서도 유지됩니다.

## 체크 사항

- [ ] configureStateless/Authorization/Jwt의 책임과 requireScope의 제한된 공통화 범위를 설명한다.
- [ ] 광범위한 허용보다 구체적인 제한을 먼저 선언하는 이유를 설명한다.
- [ ] 일반 토큰으로 회원 조회 200, 관리 조회 403을 재현한다.
- [ ] 토큰 누락/변조/로그아웃 뒤 401을 재현한다.
- [ ] JWT 오류와 ID 검증 400/데이터 부재 404/요청 제한 429를 구분한다.
- [ ] 새로운 feature의 HTTP 정책과 업무 소유권 검사를 어느 계층에 추가할지 설명한다.

참고: [Spring Security 인가 규칙](https://docs.spring.io/spring-security/reference/7.0/servlet/authorization/authorize-http-requests.html).


## 검증 기록 — 2026-09-29

- Gateway 59개, Backend 58개, PostgreSQL/Redis 통합 6개: **123개 통과**.
- 관리 경로의 양쪽 scope 요구, 일반 경로보다 먼저 적용되는 정책, 허용되지 않은 HTTP 메서드 차단을 HTTP 테스트로 확인했습니다.
- member에서 잘못된 서명/issuer/audience/만료/비활성 토큰의 401, scope 부족의 403, 인증·인가 이후 ID 검증의 400을 확인했습니다.
- 기존 PathVariable/RequestParam 타입 오류가 500으로 처리되던 공통 Advice를 400으로 보완했습니다. 오류 원문 입력값은 반환하지 않습니다.
- Compose Gateway/Backend 이미지를 갱신하고 기존 PostgreSQL/관측 overlay를 유지했습니다. Redis/DB 데이터는 초기화하지 않았습니다.
- 실제 `check-member-security.sh`에서 누락 토큰 401, 회원 목록 200, 관리 권한 부족 403, 문자/0 ID 400, 미존재 회원 404, 변조/로그아웃 토큰 401을 확인했습니다.
- 앱 healthy 및 Prometheus 두 target UP, 셸 문법과 diff 형식을 확인했습니다. Kubernetes 실제 배포는 이번에 실행하지 않았습니다.
