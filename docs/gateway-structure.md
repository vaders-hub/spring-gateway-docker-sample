# Gateway 구조와 파일별 기능

Spring Cloud Gateway(WebFlux/Netty) 기반 진입점이다. 외부 요청을 받아 JWT를 검증하고, 사용자별 요청 제한을 적용한 뒤 `/api/**`를 Backend로 전달한다. 학습용 데모 로그인(토큰 발급·로그아웃)도 이 모듈이 담당한다.

- 모듈 경로: `gateway/`
- 루트 패키지: `com.example.gateway`
- 포트: `8080` (Compose에서 호스트에 공개되는 유일한 애플리케이션 포트)
- 웹 스택: Spring WebFlux (Reactor `Mono` 기반 비동기). 요청 처리 중 `.block()`을 호출하지 않는다.
- 함께 보는 문서: [backend-structure.md](backend-structure.md), [package-structure.md](package-structure.md), [api-contract.md](api-contract.md)

## 1. 요청 처리 흐름

```text
Client
  │  Authorization: Bearer <JWT>, X-Request-Id(선택)
  ▼
RequestIdWebFilter          요청 ID 정규화, 요청 로그(http_request)
  ▼
Spring Security (SecurityConfig)
  ├─ /auth/login, /auth/token   → 인증 없이 허용 (데모 발급이 켜진 경우만 Controller 존재)
  ├─ /actuator/health/**, info  → 허용
  ├─ /api/actuator/**, /api/error/** → 거부
  ├─ /api/** GET·HEAD           → SCOPE_api.read 필요
  ├─ /api/** POST·PUT·PATCH·DELETE → SCOPE_api.write 필요
  └─ JWT 검증 (JwtConfig): 서명·issuer·audience·만료·sub, 데모 모드는 Redis 활성 토큰 확인
  ▼
라우팅 (application.yml: route id = backend-api, Path=/api/**)
  ├─ RequestHeadersFilter (GlobalFilter)  X-Request-Id 전달, X-Gateway-User 재설정
  ├─ RequestRateLimiter                    JWT 사용자별 Redis 토큰 버킷 (초과 시 429)
  └─ StripPrefix=1                         /api/orders → /orders
  ▼
Backend :8081 (JWT를 다시 검증)
```

오류 응답은 경로와 관계없이 RFC 9457 Problem Details(`application/problem+json`)로 통일된다. 처리 위치는 세 곳이다.

| 발생 위치 | 처리 클래스 | 예 |
|---|---|---|
| Security 필터 (인증·인가 실패) | `common.security.SecurityProblemWriter` | 401, 403, 토큰 저장소 장애 503 |
| Controller 처리 중 | `auth.web.AuthExceptionHandler` → `common.exception.GlobalExceptionHandler` | 로그인 실패 401, DTO 검증 400 |
| 라우팅·필터·업스트림 | `common.exception.GatewayErrorHandler` | rate limit 429, 연결 실패 502, 시간 초과 504 |

## 2. 패키지 구조

```text
com.example.gateway
├── GatewayApplication.java
├── auth                          데모 로그인 기능 (local/dev/test, oidc 아님, 설정으로 켠 경우만)
│   ├── error                     AuthErrorCode, InvalidCredentialsException
│   ├── repository                LoginSessions (저장 인터페이스)
│   │   └── redis                 RedisLoginSessions (Redis 구현)
│   ├── service                   LoginService, TokenService
│   └── web                       AuthController, AuthExceptionHandler
│       └── dto                   TokenRequest, TokenResponse
└── common                        기능과 무관한 공통 설정·보안·오류·웹 처리
    ├── config                    Security, JWT, CORS, rate limit key, 시계, 데모 발급 조건
    │   ├── properties            SecurityProperties, ObservabilityProperties
    │   └── runtime               RuntimeProfileGuard
    ├── exception                 GatewayErrorHandler, GatewayErrorResponses,
    │                             GlobalExceptionHandler, ProblemResponseWriter
    ├── security                  SecurityProblemWriter
    │   └── token                 ActiveTokenStore, TokenStoreUnavailableException
    └── web                       RequestContext, RequestIdWebFilter, RequestHeadersFilter
```

의존 방향 규칙(`ArchitectureTest`로 검사):

- `service`는 `web`이나 Redis 구현(`repository.redis`)을 참조하지 않는다.
- `repository`는 `web`, `service`를 참조하지 않는다.
- `common`은 `auth` 기능을 참조하지 않는다.
- 최상위 패키지(`auth`, `common`) 사이에 순환이 없다.

## 3. 파일별 기능

### 3.1 진입점

| 파일 | 기능 |
|---|---|
| `GatewayApplication.java` | Spring Boot 시작 클래스. `@SpringBootApplication`으로 `com.example.gateway` 하위를 컴포넌트 스캔한다. |

### 3.2 `auth` — 데모 로그인

이 패키지의 Bean은 모두 `@ConditionalOnDemoIssuer`가 붙어 있어 **`local`·`dev`·`test` 프로필이면서 `oidc`가 아니고, `app.security.demo-user.enabled=true`일 때만** 등록된다. 운영(`staging`, `prod`)과 Keycloak(`oidc`) 모드에서는 로그인 API 자체가 존재하지 않는다.

| 파일 | 기능 |
|---|---|
| `auth/error/AuthErrorCode.java` | 인증 기능 전용 오류 코드. `INVALID_CREDENTIALS` 하나이며 HTTP 401, 고정 문구를 반환한다. `platform-core`의 `ErrorCode`를 구현한다. |
| `auth/error/InvalidCredentialsException.java` | 사용자명·비밀번호가 맞지 않을 때 `TokenService`가 던지는 예외. `AuthExceptionHandler`가 401 Problem Details로 바꾼다. |
| `auth/repository/LoginSessions.java` | 로그인 기능이 요구하는 저장 인터페이스. `activate(token, ttl)`(로그인 시 토큰 활성화), `deactivate(token)`(로그아웃) 두 메서드를 `Mono`로 정의한다. |
| `auth/repository/redis/RedisLoginSessions.java` | `LoginSessions`의 Redis 구현. 실제 저장은 공통 `ActiveTokenStore`에 위임해, 로그인 쓰기와 JWT 검증 읽기가 같은 Redis 키 규칙을 쓰게 한다. |
| `auth/service/TokenService.java` | 데모 계정 확인과 JWT 발급. 사용자명·비밀번호를 상수 시간 비교(`MessageDigest.isEqual`)로 확인하고, HS256으로 서명한 토큰을 만든다. 클레임: `jti`(UUID), `iss`, `sub`(사용자명), `aud`, `iat`, `exp`, `scope="api.read api.write"`. 결과 `IssuedToken`은 `toString()`에서 토큰을 가린다. |
| `auth/service/LoginService.java` | 로그인·로그아웃 유스케이스. 로그인은 토큰 발급 후 Redis 활성화가 성공해야 토큰을 반환한다(저장 실패 시 토큰을 내보내지 않음). 로그아웃은 현재 요청의 토큰 한 개만 비활성화한다. |
| `auth/web/AuthController.java` | `POST /auth/login`, `POST /auth/token`(구 경로 호환) → 토큰 발급, `POST /auth/logout` → 현재 토큰 비활성화. 응답은 `ApiResponses.success`로 감싸고 `Cache-Control: no-store`를 붙인다(`SuccessCode.TOKEN_ISSUED`, `LOGGED_OUT`). |
| `auth/web/AuthExceptionHandler.java` | `@RestControllerAdvice(basePackageClasses = AuthController.class)`로 `AuthController` 패키지와 하위 패키지의 Controller에 적용한다. `InvalidCredentialsException` → 401, `TokenStoreUnavailableException` → 503. 현재 두 클래스는 `auth.web`에 함께 두지만, Advice 자체의 같은 패키지 배치는 프레임워크 필수 조건이 아니다. |
| `auth/web/dto/TokenRequest.java` | 로그인 요청 본문. `username`(필수, 최대 100자), `password`(필수, 최대 256자). `toString()`에서 자격 증명을 가린다. |
| `auth/web/dto/TokenResponse.java` | 로그인 응답 데이터. `accessToken`, `tokenType`(`Bearer`), `expiresIn`(초). `toString()`에서 토큰을 가린다. |

### 3.3 `common/config` — 설정

| 파일 | 기능 |
|---|---|
| `SecurityConfig.java` | WebFlux Security 필터 체인. 세션·CSRF·폼 로그인·HTTP Basic을 끄고(무상태 Bearer API), 경로별 인가 규칙(1장 흐름도 참고)과 OAuth2 Resource Server(JWT)를 설정한다. 데모 모드는 `scope` 클레임, OIDC 모드는 `permissions` 클레임을 권한으로 쓴다(`JwtAuthorities`). `/actuator/prometheus`는 `app.observability.prometheus-public`이 true일 때만 공개한다. |
| `JwtConfig.java` | `ReactiveJwtDecoder` Bean. 데모 모드는 HS256 공유 비밀키, OIDC 모드는 설정된 JWKS URI(RS256)로 서명을 검증한다. 공통으로 issuer, audience, 만료, `sub` 존재를 검사한다. 데모 모드에서는 서명 검증 후 Redis 활성 토큰 목록을 확인해, 로그아웃된 토큰을 거부하고 Redis 장애는 503(`temporarily_unavailable`)으로 구분한다. |
| `AuthIssuerConfig.java` | 데모 토큰 발급용 `JwtEncoder`(HS256) Bean. `@ConditionalOnDemoIssuer`라 발급 기능이 꺼지면 함께 사라진다. 검증용 decoder와 분리돼 있다. |
| `ConditionalOnDemoIssuer.java` | 데모 발급 관련 Bean에 붙이는 조건 애너테이션. `@Profile("!oidc & (local | dev | test)")` + `app.security.demo-user.enabled=true`. Controller, Service, Encoder, Advice가 항상 같은 조건으로 켜지고 꺼진다. |
| `CorsConfig.java` | 브라우저 CORS 정책. 허용 Origin은 설정값(`CORS_ALLOWED_ORIGINS`), 메서드는 GET·HEAD·POST·PUT·PATCH·DELETE·OPTIONS, 헤더는 `Authorization`, `Content-Type`, `X-Request-Id`. 응답의 `X-Request-Id`를 JavaScript가 읽을 수 있게 노출한다. 자격 증명(쿠키) 전송은 허용하지 않는다. |
| `GatewayFilterConfig.java` | Rate limiter가 쓰는 `principalKeyResolver` Bean. 클라이언트 헤더가 아니라 Security가 검증한 Principal 이름(JWT `sub`)을 Redis 버킷 키로 쓴다. `application.yml`의 `#{@principalKeyResolver}`가 이 Bean을 참조한다. |
| `TimeConfig.java` | UTC `Clock` Bean. 토큰 발급 시각 계산에 주입되며 테스트에서 고정 시계로 바꿀 수 있다. |
| `properties/SecurityProperties.java` | `app.security.*` 설정을 record로 바인딩하고 시작 시 검증한다. `jwt`(issuer, audience, secret 32자 이상 또는 JWKS URI 중 하나만, ttl은 1초 이상 정수 초), `demoUser`(켜진 경우 사용자명 필수, 비밀번호 12자 이상), `cors`(정확한 HTTP(S) Origin만, 와일드카드·경로 금지, maxAge 양수). `toString()`에서 비밀값을 가린다. |
| `properties/ObservabilityProperties.java` | `app.observability.prometheus-public` 값 하나를 바인딩한다. |
| `runtime/RuntimeProfileGuard.java` | 시작 시 프로필 조합을 검사해 잘못되면 기동을 실패시킨다. lifecycle 프로필(`local`, `dev`, `test`, `staging`, `prod`)은 정확히 하나, 추가로 허용되는 것은 `oidc`뿐이다. `local`·`test` 외에서 Prometheus 공개 금지, `staging`·`prod`·`oidc`에서 데모 발급 금지. |

### 3.4 `common/exception` — 오류 응답

| 파일 | 기능 |
|---|---|
| `GlobalExceptionHandler.java` | Controller 처리 중 예외의 공통 Advice(기능 Advice 다음 순서). DTO 검증 실패(`WebExchangeBindException`) → 400 + 필드별 `errors`, 본문 누락·형식 오류 → 400, 그 밖의 예외 → `GatewayErrorResponses`로 위임. |
| `GatewayErrorHandler.java` | WebFlux 전역 `ErrorWebExceptionHandler`(Boot 기본 handler보다 먼저, `@Order(-2)`). Advice가 처리하지 못한 필터·라우팅 예외를 Problem Details로 쓴다. 이미 전송이 시작된 응답은 덮어쓰지 않는다. |
| `GatewayErrorResponses.java` | 예외 → HTTP 상태 매핑. 프레임워크 `ErrorResponse`의 상태·헤더 보존, rate limiter의 429와 함께 전달된 응답 헤더 보존, 라우팅 중 연결 실패·DNS·TLS 오류 → 502, 시간 초과 → 504, 그 외 500. 5xx는 요청 ID·예외 타입·원인 위치를 `gateway_request_error`로 로그에 남긴다(예외 메시지는 남기지 않음). |
| `ProblemResponseWriter.java` | Security writer와 전역 오류 handler가 공유하는 응답 쓰기 도구. 상태·헤더·`X-Request-Id`를 설정하고, 이전 본문의 길이·압축 헤더를 지운 뒤 JSON을 쓴다. HEAD 요청은 본문 없이 끝낸다. |

### 3.5 `common/security` — 보안 보조

| 파일 | 기능 |
|---|---|
| `SecurityProblemWriter.java` | Security 단계의 인증 실패(401)·권한 부족(403)을 Problem Details로 쓴다. 원인이 토큰 저장소 장애면 401 대신 503을 쓴다. |
| `token/ActiveTokenStore.java` | Redis 활성 토큰 목록(비동기 `ReactiveStringRedisTemplate`). `activate`(SET + TTL 한 번에), `isActive`(키 존재 확인), `deactivate`(DEL). 키는 토큰 원문이 아니라 SHA-256 값(`TokenKey.of`)이다. JVM 캐시가 없어 여러 인스턴스에 로그아웃이 즉시 반영된다. |
| `token/TokenStoreUnavailableException.java` | Redis 장애를 잘못된 토큰(401)과 구분하기 위한 예외. `causedBy()`로 원인 체인을 10단계까지 확인한다. |

### 3.6 `common/web` — 요청 ID와 헤더

| 파일 | 기능 |
|---|---|
| `RequestContext.java` | 헤더·속성 이름 상수(`X-Request-Id`, `X-Gateway-User`)와 요청 ID 처리 도구. 클라이언트가 보낸 요청 ID는 영문·숫자·`._-` 1~64자만 받아들이고, 아니면 새 UUID를 쓴다. |
| `RequestIdWebFilter.java` | 모든 HTTP 요청의 첫 필터(Security보다 앞). 요청 ID를 확정해 요청·응답 헤더와 exchange 속성에 넣고, 요청이 끝나면 method·path·status·소요 시간을 `http_request` 로그로 남긴다. 정상 health 요청은 DEBUG로 낮춘다. |
| `RequestHeadersFilter.java` | 라우팅되는 요청에만 적용되는 Gateway `GlobalFilter`. Backend로 `X-Request-Id`를 전달하고, 클라이언트가 보낸 `X-Gateway-User`를 지운 뒤 인증된 사용자 이름으로 다시 채운다(위조 방지). Backend는 이 헤더가 아니라 JWT로 사용자를 판단한다. |

## 4. 설정 파일 (`src/main/resources`)

| 파일 | 내용 |
|---|---|
| `application.yml` | 공통 설정. 서버 포트 8080, graceful shutdown, Netty 타임아웃, Redis 연결, **Gateway 라우트**(`backend-api`: `Path=/api/**` → `BACKEND_URL`, 필터 `RequestRateLimiter`(초당 2개, 최대 5개, 초과 시 예외) + `StripPrefix=1`), Backend 호출용 HTTP 클라이언트 연결 풀·타임아웃, `app.security.*`(JWT, 데모 사용자 기본 꺼짐, CORS), Actuator(health/info/prometheus, liveness와 readiness 분리, readiness에 Redis 포함), 지표 histogram, tracing(기본 export 꺼짐). |
| `application-local.yml` | 로컬 학습. 데모 발급 켬, tracing 100% 샘플링, `com.example.gateway` DEBUG 로그. |
| `application-dev.yml` | 개발. 데모 발급 켬, tracing 100%, DEBUG 로그. |
| `application-test.yml` | 테스트. 데모 발급 켬, OTLP 지표·tracing export 끔. |
| `application-staging.yml` | 운영 전 검증. CORS Origin 필수(환경변수), INFO 로그, ECS JSON 구조화 로그. |
| `application-prod.yml` | 운영. CORS Origin 필수, INFO 로그(Gateway 라이브러리는 WARN), ECS JSON 로그. |
| `application-oidc.yml` | Keycloak 인증. issuer·JWKS URI를 Keycloak으로 바꾸고 공유 비밀키를 비우며 데모 발급을 끈다. |

주요 환경변수: `BACKEND_URL`, `REDIS_HOST`, `REDIS_PORT`, `JWT_ISSUER`, `JWT_AUDIENCE`, `JWT_SECRET`, `JWT_TTL`, `DEMO_USERNAME`, `DEMO_PASSWORD`, `CORS_ALLOWED_ORIGINS`, `RATE_LIMIT_REPLENISH_RATE`, `RATE_LIMIT_BURST_CAPACITY`, `OIDC_ISSUER`, `OIDC_JWK_SET_URI`, `PROMETHEUS_PUBLIC`.

## 5. 빌드와 이미지

| 파일 | 내용 |
|---|---|
| `gateway/build.gradle` | Java 25 toolchain. 의존성: `platform-core`, Spring Cloud Gateway(WebFlux), Actuator, Reactive Redis, Security, OAuth2 Resource Server, Validation, OpenTelemetry, Prometheus. 산출물 이름 `app.jar`. `-PskipArchitecture`로 `architecture` 태그 테스트 제외 가능. |
| `gateway/Dockerfile` | 멀티스테이지 빌드. `gradle:9.7.1-jdk25`에서 `:gateway:bootJar` → `eclipse-temurin:25-jre` 실행 이미지. UID 10001 비루트 사용자, healthcheck용 `wget` 포함. |

## 6. 테스트 (`src/test`)

| 파일 | 검증 내용 |
|---|---|
| `architecture/ArchitectureTest` | 2장의 패키지 의존 방향 규칙 4개. `@Tag("architecture")`. |
| `auth/service/TokenServiceTest` | 토큰 만료 시각이 주입된 시계 기준인지, 잘못된 자격 증명은 encoder까지 가지 않는지. |
| `common/config/properties/SecurityPropertiesTest` | 설정 검증 규칙(와일드카드 Origin, 짧은 비밀키, TTL, 데모 자격 증명, JWKS와 비밀키 혼용 금지, 비밀값 가림). |
| `common/config/runtime/RuntimeProfileGuardTest` | 프로필 조합 허용·거부, 운영에서 데모 발급·Prometheus 공개 금지. |
| `common/exception/GatewayErrorHandlerTest` | 이미 전송된 응답을 덮어쓰지 않는지. |
| `common/exception/GatewayErrorHttpIntegrationTest` | 실제 HTTP로 429(헤더 보존, Backend 미도달), 502, 504, 500, 404·405, HEAD 오류, 관리 경로 차단, `;` 경로 거부. |
| `common/exception/GlobalExceptionHandlerTest` | 예상치 못한 오류의 안전한 메시지, 405와 `Allow` 헤더 보존. |
| `common/security/SecurityHttpIntegrationTest` | 401·403, scope별 접근, audience·issuer·서명·만료 검증, 로그인·로그아웃과 토큰 비활성화, Redis 장애 시 503, 명시적 거부 우선순위. |
| `common/security/OidcHttpIntegrationTest` | `oidc` 프로필에서 JWKS 기반 검증. |
| `common/security/ProductionContextTest` | `prod`에서 데모 발급 Bean이 등록되지 않는지. |
| `common/security/PublicPrometheusHttpIntegrationTest` | 공개 설정 시 토큰 없이 Prometheus 수집 가능. |
| `common/security/SecurityProblemWriterTest` | 401 JSON과 `WWW-Authenticate: Bearer`, 자격 증명 비노출. |
| `common/web/RequestHeadersFilterTest` | 위조된 `X-Gateway-User` 제거·교체, Principal 조회 실패 시 전달 중단. |

## 7. 의존하는 공통 모듈 (`libs/platform-core`)

Gateway와 Backend가 함께 쓰는 응답·오류·인증 계약이다. 상세 표는 [backend-structure.md의 7장](backend-structure.md#7-공통-모듈-libsplatform-core)을 참고한다.

Gateway가 주로 쓰는 것: `ApiResponses`·`SuccessCode`(로그인 응답), `ProblemDetails`·`CommonErrorCode`(오류 응답), `JwtAuthorities`(권한 변환), `TokenKey`(Redis 키 규칙), `ErrorDiagnostics`(오류 로그).
