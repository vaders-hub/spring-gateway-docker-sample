# Feature-based Structure와 설정 책임

2026-09-29부터 기능을 최상위 패키지로 묶고, 각 기능 안에서 controller/service/dto 등의
역할을 나눕니다. Backend의 루트 controller/service/dto와 Gateway의 루트 filter는 사용하지 않습니다.
기능과 관계없는 앱 전체 설정은 config, 여러 기능이 공유하는 응답/추적 코드는 common에 둡니다.

## 현재 구조

```text
com.example.backend
├── BackendApplication
├── hello/
│   ├── controller/   HelloController
│   ├── service/      HelloService와 업무 결과 record
│   └── dto/          HelloResponse, EchoRequest, EchoResponse
├── security/
│   ├── config/       SecurityConfig, JwtConfig
│   ├── properties/   JwtProperties
│   └── web/          SecurityProblemWriter
├── config/           TimeConfig, runtime/RuntimeProfileGuard, properties/ObservabilityProperties
└── common/           api, code, error, util, web

com.example.gateway
├── GatewayApplication
├── auth/
│   ├── controller/   AuthController
│   ├── service/      TokenService, InvalidCredentialsException
│   ├── dto/          TokenRequest, TokenResponse
│   ├── config/       AuthIssuerConfig, ConditionalOnDemoIssuer
│   └── error/        AuthExceptionHandler
├── routing/
│   ├── config/       GatewayFilterConfig (principalKeyResolver)
│   └── filter/       RequestHeadersFilter
├── security/
│   ├── config/       SecurityConfig, JwtConfig, CorsConfig
│   ├── properties/   SecurityProperties
│   └── web/          SecurityProblemWriter
├── config/           TimeConfig, runtime/RuntimeProfileGuard, properties/ObservabilityProperties
└── common/           api, code, error, util, web
```

`hello`는 현재 hello/echo 샘플 use case를 함께 소유합니다. 둘을 HTTP endpoint 하나씩 별도 기능으로
쪼개지 않습니다. Gateway의 `auth`는 데모 토큰 발급, `security`는 요청 인증/인가,
`routing`은 인증 이후 Backend 전달과 요청 제한 설정을 소유합니다.

| 패키지 | 책임 |
|---|---|
| hello/controller, hello/service, hello/dto | Backend HTTP 경계, 업무 처리, 외부 계약 |
| auth/controller, auth/service, auth/dto | Gateway 토큰 발급 HTTP 경계, use case, 외부 계약 |
| auth/config, auth/error | 데모 발급 Bean 조건/encoder, InvalidCredentialsException 처리 |
| routing/config, routing/filter | rate-limit key resolver, Gateway 라우팅 단계 헤더 전달 |
| security/config, security/properties, security/web | JWT 검증·인가·CORS, 보안 설정 record, Security 401/403 응답 |
| config | UTC Clock 등 앱 전체 구성 |
| config/runtime, config/properties | lifecycle profile guard, 공통 관측 설정 ObservabilityProperties |
| common/web | Request ID 정규화·요청 로그·HTTP 요청 문맥 |
| common/error | 표준 입력 오류/예상치 못한 오류의 Advice, ProblemDetails, Servlet/WebFlux 오류 adapter |
| common/api | ApiResponse와 Controller용 ApiResponses.success/fail |
| common/code | 공통 응답 enum SuccessCode/ErrorCode |
| common/util | 프레임워크 독립 보조 함수 ErrorDiagnostics |

## 의존 방향과 기능 경계

- 업무 기능은 common을 사용하며 **common은 hello/auth/security/routing을 직접 참조하지 않습니다.**
- 인증 전용 예외는 `auth/error/AuthExceptionHandler`에서 처리합니다. 공통 Advice는 auth의 예외 타입을 알지 않습니다.
  인증 Advice가 먼저 실행되고, DTO/JSON 검증 및 다른 오류는 공통 Advice로 이어집니다.
- `auth → security.properties`는 JWT·데모 발급 정책을 공유하기 위한 명시적 의존입니다.
  기존 app.security 설정 접두사와 환경변수 계약을 유지하려고 SecurityProperties record를 공유합니다.
  `security`는 auth에 의존하지 않으며 JwtConfig는 검증 decoder만, AuthIssuerConfig는 발급 encoder만 만듭니다.
- routing은 Security가 검증한 Principal을 사용합니다. Controller/TokenService를 호출하여 인증을 재구현하지 않습니다.
- 앱 전체 구성인 config는 업무 Controller/Service를 직접 호출하지 않습니다. profile guard는 Environment 설정을 검사합니다.
- 기능 사이 협력이 필요하면 상대 기능의 공개 서비스/API를 사용하고 Controller·DTO·내부 저장소를 공유하지 않습니다.
  현재는 Java 패키지 경계이며 별도 Gradle 모듈이나 강제 모듈 격리 체계는 아닙니다.

HTTP 진입점은 controller, 업무 로직과 transaction 경계는 service로 이름을 통일합니다.
Service 인터페이스/Impl, domain/entity/repository는 실제 필요가 생길 때 추가합니다.
Backend Service는 HTTP DTO/requestId 대신 업무 인자와 결과 record를 사용하고 Controller가 변환합니다.
Actuator/Prometheus와 streaming까지 자동으로 감싸는 전역 Advice는 사용하지 않습니다.

새 코드의 기준: **업무 Controller는 ApiResponses, 오류 처리기는 ProblemDetails**를 사용합니다.
ApiResponses.fail은 common/error/ProblemDetails로 위임하며 오류 처리기는 common/api를 참조하지 않습니다.
공통 enum/util을 무조건 기능으로 복제하지 않고 기존 common.code/common.util을 유지합니다.

## 새 기능을 추가하는 순서

1. 예를 들어 `order` 기능이라면 `order/controller`, `order/service`, `order/dto`에 필요한 클래스부터 만듭니다.
2. 기능 전용 설정·예외 처리가 필요할 때만 `order/config`, `order/error`를 추가합니다.
   전용 Advice는 해당 Controller 범위로 제한하고, 공통 오류 생성 규칙을 재사용합니다.
3. 새 API의 경로·메서드·scope를 `security/config/SecurityConfig`에 명시합니다. Backend의 기본 거부 정책은 유지합니다.
4. 단위 테스트는 main과 같은 기능 패키지에 둡니다. 보안 HTTP 테스트는 security, 공통 오류 통합 테스트는 common/error에 유지합니다.
5. API 계약·관련 학습 문서와 테스트를 함께 갱신합니다. 실제 기능 없이 빈 패키지나 공통 인터페이스를 미리 만들지 않습니다.

실행 클래스는 각 앱 루트 패키지에 유지하므로 component scan과 테스트의 Boot 설정 탐색이 하위 기능을 포함합니다.
설정 클래스는 proxyBeanMethods=false, 생성자/Bean 매개변수 주입을 유지합니다.
application YAML은 각 모듈의 src/main/resources에 유지하며 URL·Bean 이름·설정 키는 바꾸지 않았습니다.
Gateway(WebFlux)와 Backend(Servlet)의 독립 빌드 경계와 각자의 common 패키지도 유지합니다.
공유 HTTP 계약은 [api-contract.md](api-contract.md)로 관리합니다.

## 공통 코드와 유틸리티 배치 기준

각 모듈은 다음 구조를 사용합니다. 두 서비스의 독립 빌드 경계는 유지합니다.

```text
common/
├── code/   SuccessCode, ErrorCode
├── util/   ErrorDiagnostics
├── api/    ApiResponse, ApiResponses
├── error/  ProblemDetails, Advice, 오류 writer/handler
└── web/    RequestContext, 요청 추적 filter
```

- 공통 응답 enum은 `common.code`에 둡니다. 주문 상태처럼 특정 업무에 속하는 enum은 해당 업무 패키지에 둡니다.
- 재사용 가능한 순수 보조 함수는 `common.util`에 둡니다. `ErrorDiagnostics`는 Throwable을 받아 안전한 진단 값만 반환하고 직접 로그를 쓰지 않습니다.
- static 메서드가 있다는 이유만으로 util로 옮기지는 않습니다. HTTP 응답 생성은 api/error, HTTP 요청 문맥은 web에 둡니다.
- `code`와 `util`은 Controller/Service/응답 팩토리를 참조하지 않습니다. `api → error`, `api/error → code`, `error → util` 방향을 유지합니다.
- `ErrorDiagnostics`와 `causes`는 다른 패키지에서 사용하므로 public입니다. 생성자는 private으로 유지합니다.

## 설정 객체와 애너테이션

`@ConfigurationProperties`는 설정 접두사와 Java 타입을 연결하는 애너테이션입니다.
`ConfigurationProperties`라는 프로젝트 설정 파일이나 객체로 값이 전달되는 것은 아닙니다.

| 구분 | 현재 코드의 역할 |
|---|---|
| 설정 원본 | Compose의 `environment` 또는 Kubernetes Deployment의 `envFrom`/`env`가 컨테이너 환경변수를 전달 |
| `application.yml` / `application-{profile}.yml` | 공통값과 profile별 값을 정의하며 `${...}`로 외부 값을 참조 |
| Spring `Environment` | 환경변수·설정 파일 등 여러 설정 소스를 우선순위에 따라 조회하고 플레이스홀더 해석에 사용 |
| `@ConfigurationProperties` | 접두사 아래의 값을 설정 객체에 타입 변환하여 바인딩 |
| `@EnableConfigurationProperties` | 각 모듈의 `security/config/SecurityConfig`에서 보안·관측 설정 타입을 Spring Bean으로 등록 |
| `@Validated`, `@Valid`, 검증 제약 및 record 생성자 | 필수값·길이·TTL·CORS 등 해당 설정의 조건을 검증. `@ConfigurationProperties`만으로 모든 조건이 검증되지는 않음 |
| `RuntimeProfileGuard` | 별도 Bean의 `@PostConstruct`에서 `Environment`를 직접 조회해 profile과 금지 설정 조합을 검사 |

실제 바인딩 대상은 Gateway의 [SecurityProperties](../gateway/src/main/java/com/example/gateway/security/properties/SecurityProperties.java)
(`app.security`), Backend의 [JwtProperties](../backend/src/main/java/com/example/backend/security/properties/JwtProperties.java)
(`app.security.jwt`), 각 모듈의 `ObservabilityProperties` (`app.observability`)입니다.
Gateway는 토큰을 발급하므로 TTL이 있지만, Backend의 `JwtProperties`에는 TTL 필드가 없습니다.
`RuntimeProfileGuard`는 이 설정 객체들의 검증을 이어받는 마지막 단계가 아니라 독립된 시작 시 검사입니다.

모든 설정이 프로젝트의 `SecurityProperties`를 거치지는 않습니다. `server.*`, `spring.*` 등은
프레임워크가 각 설정에 맞게 처리하며, Gateway route의 rate-limit 인자도 Gateway가 처리합니다.
구체적인 값의 흐름은 [1단계 JWT_TTL 예제](learning/01-setup-and-compose.md#설정-추적-예-jwt_ttl)를 참고합니다.

## 동작 보완

- 잘못된 Bearer 토큰과 인증 누락 모두 동일 SecurityProblemWriter를 사용합니다.
- `ApiResponses.success`/`fail`과 `SuccessCode`/`ErrorCode`로 HTTP 상태·헤더·기본 오류 메시지를 공통화합니다.
- 오류 JSON은 Boot 4의 자동 구성 JsonMapper를 주입받아 직렬화합니다.
- Advice가 처리하지 못한 오류는 Backend의 Servlet `/error`와 Gateway의 `ErrorWebExceptionHandler`에서 각각 처리합니다.
- Gateway 429는 `throw-on-limit`으로 예외를 전달하고 Advice/전역 handler가 같은 `GatewayErrorResponses`를 사용하며, 이미 수신한 Backend 오류 본문은 그대로 전달합니다.
  [처리 범위와 예외](api-contract.md#시스템별-오류-처리-경계)를 참고합니다.
- ControllerAdvice는 Spring ErrorResponse의 4xx/5xx 상태와 Allow 같은 응답 헤더를 보존합니다.
- 예상하지 못한 오류는 requestId·예외 타입·원인별 첫 발생 위치를 남깁니다.
  cause는 순환 방지와 최대 8개 제한을 적용하며, 비밀값이 들어갈 수 있는 원문 메시지와
  전체 stack trace는 DEBUG에서도 출력하지 않습니다.
- local/dev 콘솔은 `%kvp`로 SLF4J key-value 필드를 표시합니다. staging/prod는 기존 ECS 출력입니다.
- 정상 health 요청 로그는 DEBUG, 오류 health 요청은 INFO로 남깁니다.
- `TimeConfig`의 UTC Clock을 HelloService/TokenService에 주입해 시간 계산을 테스트할 수 있습니다.
  static 응답 팩토리의 `meta.timestamp`는 응답 생성 시각으로 기존 `Instant.now()`를 유지합니다.
- JWT SecretKey는 주입 가능한 Bean 대신 JwtConfig/AuthIssuerConfig 내부에서 생성합니다. HS256과 UTF-8 키 형식은 유지합니다.
  audience는 표준 `JwtClaimValidator`로 검증합니다.
- JWT 키, 데모 비밀번호, 토큰 DTO의 toString은 민감값을 가립니다.
- 토큰 발급 응답은 Cache-Control: no-store, Pragma: no-cache를 보냅니다.
- Gateway는 `/api/actuator/**`·`/api/error/**`의 Backend 우회 접근을 scope 허용 전에 차단합니다.
- Backend는 명시한 경로·메서드 외에 기본 거부합니다. 새 Controller에는 명시적 인가 규칙도 필요합니다.
  내부 ERROR dispatch는 허용하여 최종 오류 응답이 401/403으로 가려지지 않게 합니다.
- 인증은 stateless로 유지하며, 인증 전 요청을 세션에 저장하는 request cache도 비활성화합니다.
- CORS는 경로/와일드카드가 없는 정확한 HTTP(S) Origin만 받습니다.
- JWT TTL은 1초 이상의 정수 초만 허용합니다.
- staging/prod에서 데모 토큰 발급을 켜면 시작에 실패합니다. 발급 Controller,
  Service, encoder에도 같은 `@ConditionalOnDemoIssuer` 제한이 있습니다.
- 공개 Prometheus는 local/test만 허용하며 dev/staging/prod에서 켜면 시작에 실패합니다.

## Gradle과 Docker 빌드 경계

루트 `settings.gradle`은 IDE/일괄 빌드를 위한 멀티프로젝트입니다.
`gateway/settings.gradle`, `backend/settings.gradle`은 각 모듈을 독립 Docker build
context로 사용하는 데 필요합니다. 중첩 프로젝트 자동 탐지는 VS Code에서 끄고
루트 프로젝트를 import합니다. 중복 파일로 보고 삭제하지 않습니다.

각 모듈 `bootJar`의 결과 이름은 `build/libs/app.jar`로 고정했습니다.
Dockerfile은 버전 문자열을 알 필요가 없으며 이미지 태그/digest와 Build Info로 버전을 식별합니다.
독립 빌드용 plugin/BOM 버전은 두 모듈과 문서를 함께 갱신합니다.
Gateway의 native-access JVM 플래그는 Netty 경로를 위한 설정으로 Backend에 기계적으로 복사하지 않습니다.

공유 convention plugin, Spotless/Checkstyle/ArchUnit은 CI 도입 시 검토합니다.
layered JAR, Foojay 자동 JDK 다운로드는 이번 변경에 포함하지 않았습니다.
기존 target/bin/Gradle cache는 Git 제외 상태를 유지하며 사용자 산출물을 임의 삭제하지 않습니다.

## 검증 범위

2026-09-29 기능별 구조 전환 후 Java 컴파일과 두 모듈의 회귀 테스트 78개가 통과했습니다.
Backend 29개, Gateway 49개이며 인증 전용 Advice와 공통 입력 검증 Advice의 처리 순서도 확인했습니다.
설정 보호, JWT/권한, DTO 검증, 오류 상태·헤더, 실제 Servlet ERROR dispatch와 Netty 라우팅을
검증합니다. Compose/kind 재배포는 이 검증과 별개이며 실행 중인 컨테이너는 변경하지 않습니다.
세부 반영·보류 이유는 [AA/SWA 검토 기록](aa-swa-review.md)을 참고합니다.
