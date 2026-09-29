# Feature-based Structure와 설정 책임

2026-09-29 사용자 제공 샘플을 기준으로 기능을 api/application/domain/infrastructure 기준으로 나누고,
설정·보안·예외 처리 등 공통 기술 코드는 common 아래로 모았습니다.
Backend는 hello와 local/test 학습용 member/product/order, Gateway는 데모 토큰 발급 auth 기능을 제공합니다.

## 현재 구조

```text
com.example.backend
├── BackendApplication
├── common/
│   ├── config/       SecurityConfig, JwtConfig, TimeConfig
│   │   ├── properties/  JwtProperties, ObservabilityProperties
│   │   └── runtime/     RuntimeProfileGuard
│   ├── exception/    GlobalExceptionHandler, ApiErrorController, ProblemDetails
│   ├── security/     SecurityProblemWriter
│   ├── response/     ApiResponse, ApiResponses
│   ├── code/         SuccessCode, ErrorCode
│   ├── util/         ErrorDiagnostics
│   └── web/          RequestContext, RequestIdFilter
└── hello/
    ├── api/          HelloController
    │   └── dto/      HelloResponse, EchoRequest, EchoResponse
    └── application/  HelloService와 업무 결과 record

com.example.gateway
├── GatewayApplication
├── common/
│   ├── config/       SecurityConfig, JwtConfig, CorsConfig, TimeConfig,
│   │                GatewayFilterConfig, AuthIssuerConfig, ConditionalOnDemoIssuer
│   │   ├── properties/  SecurityProperties, ObservabilityProperties
│   │   └── runtime/     RuntimeProfileGuard
│   ├── exception/    GlobalExceptionHandler, ProblemDetails, ProblemResponseWriter,
│   │                GatewayErrorHandler, GatewayErrorResponses
│   ├── security/     SecurityProblemWriter
│   ├── response/     ApiResponse, ApiResponses
│   ├── code/         SuccessCode, ErrorCode
│   ├── util/         ErrorDiagnostics
│   └── web/          RequestContext, RequestIdWebFilter, RequestHeadersFilter
└── auth/
    ├── api/          AuthController, AuthExceptionHandler
    │   └── dto/      TokenRequest, TokenResponse
    └── application/  TokenService와 IssuedToken, InvalidCredentialsException
```

위 트리는 기본 hello/auth 공통 구조입니다. 11단계에서 추가한 실제 학습 기능은 다음과 같습니다.

```text
member/                              product도 동일한 계층
├── api/                             Controller, MapStruct Mapper
│   └── dto/                         API 응답
├── application/                     Service, Repository 조회 계약
├── domain/                          Member / Product 업무 record
└── infrastructure/                  불변 FixtureRepository

order/
├── api/                             Controller, MapStruct Mapper
│   └── dto/                         주문 견적 요청/응답
├── application/                     OrderService, OrderCatalog 연동 계약
├── domain/                          OrderQuote 금액 계산
└── infrastructure/                  LocalOrderCatalog: 회원/상품 application 연동
```

common/config에 MappingConfig, OpenApiConfig, ConditionalOnLearningMock,
common/config/runtime에 LearningProfileGuard, common/exception에 BusinessException이 추가됩니다.
학습 feature는 local/test에서 명시적으로 켠 경우만 생성하며 저장·결제는 하지 않습니다.
[11단계](learning/11-features-and-library-roadmap.md)에 검증·확장 순서가 있습니다.

복잡한 HTTP DTO 변환이 필요하면 api/mapper를 추가합니다.
단순한 변환은 현재처럼 Controller에서 처리하고 불필요한 Mapper 인터페이스는 만들지 않습니다. 현재 목업 3개는 MapStruct 학습 대상으로 명시적 Mapper를 둡니다.

## 계층 책임과 의존 방향

| 패키지 | 책임 |
|---|---|
| feature/api | Controller, HTTP 검증 시작, 응답 변환, 기능 전용 Advice |
| feature/application | Service, use case, 업무 결과와 기능 전용 예외 |
| feature/api/dto | 해당 기능의 요청/응답 계약 |
| feature/domain (필요 시) | 업무 모델·규칙. 현재 Member/Product/OrderQuote |
| feature/infrastructure (필요 시) | 저장소/외부 연동 adapter. fixture/LocalOrderCatalog와 12단계 JPA Entity·Spring Data Repository·adapter |
| common/config | 보안·JWT·CORS·Clock·라우팅 구성 및 데모 발급 Bean 조건 |
| common/config/properties, common/config/runtime | 타입 기반 설정 객체, 시작 시 profile 보호 |
| common/security | Servlet/WebFlux 인증·인가 오류 응답 adapter |
| common/exception | 표준 검증·미처리 오류의 Advice, ProblemDetails, 전역 오류 adapter |
| common/web | 요청 문맥·로그·Request ID·라우팅 헤더 전달 |
| common/response, common/code, common/util | 공통 응답 생성, enum, 순수 진단 유틸리티 |

- 기본 방향은 api → application → domain(도입 시)입니다. application은 api와 api/dto를 참조하지 않습니다. domain은 api/infrastructure를 참조하지 않습니다.
- 기능은 common을 사용하고, **main의 common은 hello/auth/member/product/order를 직접 참조하지 않습니다.**
  공통 설정은 프레임워크 Bean과 설정 객체를 구성하며 업무 Controller/Service를 호출하지 않습니다.
- 인증 전용 InvalidCredentialsException은 application, HTTP 매핑은 api의 AuthExceptionHandler가 소유합니다.
  이 Advice는 AuthController 패키지로 범위를 제한하고, 먼저 처리하지 않는 DTO/JSON 오류는 공통 Advice가 처리합니다.
- JwtConfig는 검증 decoder, AuthIssuerConfig는 데모 발급 encoder를 만듭니다.
  app.security 접두사와 환경변수·local/dev/test 발급 조건은 유지합니다.
- RequestHeadersFilter는 인증된 Principal을 사용하며 TokenService를 호출해 인증을 재구현하지 않습니다.
- 기능 간 협력은 상대 기능의 공개 application 서비스/API를 통합니다. 다른 기능의 api나 내부 저장소에 의존하지 않습니다.

api/dto에는 기존 요청 검증 애너테이션과 JSON 계약이 유지됩니다.
따라서 이번 변경은 패키지 책임을 정리하는 것이며 프레임워크에서 완전히 분리된 도메인 아키텍처를 의미하지 않습니다.
양쪽 Service는 API DTO/requestId 대신 업무 인자와 결과 record를 사용하고 Controller가 API DTO로 변환합니다.
TokenService.issue(username, password)는 IssuedToken을 반환하고 AuthController가 TokenResponse로 변환합니다.
IssuedToken과 TokenResponse 모두 toString에서 accessToken을 가립니다. JWT 검증·발급 정책은 유지합니다.
현재 경계는 Java 패키지이며 ArchUnit 테스트가 의존 방향과 feature 순환을 검사합니다. 별도 Gradle 모듈/JPMS 격리는 아닙니다.

새 코드의 기준: **업무 Controller는 ApiResponses, 오류 처리기는 ProblemDetails**를 사용합니다.
ApiResponses.fail은 common/exception/ProblemDetails로 위임합니다. Actuator/Prometheus/streaming을 자동으로 감싸지 않습니다.

## 새 기능을 추가하는 순서

1. 예를 들어 order라면 order/api, order/application, order/api/dto에 실제 필요한 코드부터 추가합니다.
2. 업무 모델·규칙은 order/domain, Repository·외부 연동 구현은 order/infrastructure에 추가합니다. HTTP 변환이 복잡할 때 order/api/mapper를 추가합니다.
   아직 구현할 코드가 없는 빈 패키지·Service 인터페이스/Impl은 만들지 않습니다.
3. 기능 전용 예외는 application 또는 domain, HTTP 예외 처리기는 api에 두고 해당 Controller로 범위를 제한합니다.
4. 경로·메서드·scope를 common/config/SecurityConfig에 등록합니다. Backend의 기본 거부 정책은 유지합니다.
5. 테스트는 main과 같은 패키지를 따릅니다. 보안 통합 테스트는 common/security, 공통 오류 테스트는 common/exception에 둡니다.
6. API 계약·학습 문서와 검증을 함께 갱신합니다. 이동 후 남은 빈 src 디렉터리는 비어 있음을 확인한 뒤 삭제합니다.

각 Application 클래스는 앱 루트에 유지하여 component scan과 Boot 테스트 설정 탐색이 하위 패키지를 포함합니다.
생성자/Bean 매개변수 주입, proxyBeanMethods=false, application YAML 위치, URL·Bean 이름·설정 키는 유지합니다.
Gateway(WebFlux)와 Backend(Servlet)의 독립 빌드 경계와 각자의 common도 유지합니다.
공유 HTTP 계약은 [api-contract.md](api-contract.md)로 관리합니다.

## 공통 코드와 유틸리티 배치 기준

- 샘플의 common/exception 배치를 따르되, 이전에 정한 enum 분리 기준에 따라 SuccessCode/ErrorCode는 common.code에 함께 둡니다.
  주문 상태처럼 특정 업무에 속한 enum은 해당 기능의 domain에 둡니다.
- ErrorDiagnostics는 common.util의 순수 진단 함수입니다. 생성자는 private이고 다른 패키지에서 쓰는 함수는 public입니다.
- static이라는 이유만으로 util로 옮기지 않습니다. HTTP 응답 생성은 response/exception, 요청 문맥은 web에서 담당합니다.
- response → exception, response/exception → code, exception → util 방향을 유지합니다. code/util은 Controller/Service/응답 팩토리를 참조하지 않습니다.

## 설정 객체와 애너테이션

`@ConfigurationProperties`는 설정 접두사와 Java 타입을 연결하는 애너테이션입니다.
`ConfigurationProperties`라는 프로젝트 설정 파일이나 객체로 값이 전달되는 것은 아닙니다.

| 구분 | 현재 코드의 역할 |
|---|---|
| 설정 원본 | Compose의 `environment` 또는 Kubernetes Deployment의 `envFrom`/`env`가 컨테이너 환경변수를 전달 |
| `application.yml` / `application-{profile}.yml` | 공통값과 profile별 값을 정의하며 `${...}`로 외부 값을 참조 |
| Spring `Environment` | 환경변수·설정 파일 등 여러 설정 소스를 우선순위에 따라 조회하고 플레이스홀더 해석에 사용 |
| `@ConfigurationProperties` | 접두사 아래의 값을 설정 객체에 타입 변환하여 바인딩 |
| `@EnableConfigurationProperties` | 각 모듈의 `common/config/SecurityConfig`에서 보안·관측 설정 타입을 Spring Bean으로 등록 |
| `@Validated`, `@Valid`, 검증 제약 및 record 생성자 | 필수값·길이·TTL·CORS 등 해당 설정의 조건을 검증. `@ConfigurationProperties`만으로 모든 조건이 검증되지는 않음 |
| `RuntimeProfileGuard` | 별도 Bean의 `@PostConstruct`에서 `Environment`를 직접 조회해 profile과 금지 설정 조합을 검사 |

실제 바인딩 대상은 Gateway의 [SecurityProperties](../gateway/src/main/java/com/example/gateway/common/config/properties/SecurityProperties.java)
(`app.security`), Backend의 [JwtProperties](../backend/src/main/java/com/example/backend/common/config/properties/JwtProperties.java)
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

ArchUnit은 11단계에서 적용했습니다. 공유 convention plugin과 Spotless/Checkstyle은 CI 도입 시 검토합니다.
layered JAR, Foojay 자동 JDK 다운로드는 이번 변경에 포함하지 않았습니다.
기존 target/bin/Gradle cache는 Git 제외 상태를 유지하며 사용자 산출물을 임의 삭제하지 않습니다.

## 검증 범위

11단계 목업/라이브러리 추가 후 96개 회귀 테스트가 통과했습니다. [1차 검증 기록](learning/11-features-and-library-roadmap.md#검증과-단계-체크)을 참고합니다.
아래 78개 기록은 패키지 전환 시점의 이전 검증입니다.

2026-09-29 기능별 구조 전환 후 Java 컴파일과 두 모듈의 회귀 테스트 78개가 통과했습니다.
Backend 29개, Gateway 49개이며 인증 전용 Advice와 공통 입력 검증 Advice의 처리 순서도 확인했습니다.
설정 보호, JWT/권한, DTO 검증, 오류 상태·헤더, 실제 Servlet ERROR dispatch와 Netty 라우팅을
검증합니다. Compose/kind 재배포는 이 검증과 별개이며 실행 중인 컨테이너는 변경하지 않습니다.
세부 반영·보류 이유는 [AA/SWA 검토 기록](aa-swa-review.md)을 참고합니다.


## 12단계 저장소 경계

member/product의 application Repository port는 fixture와 JPA adapter가 각각 구현합니다.
order/application의 OrderPlacementService가 저장 transaction과 소유권 조회를 담당하고,
OrderRepository port를 order/infrastructure의 JpaOrderRepository가 구현합니다.
JPA Entity와 Spring Data Repository는 각 feature/infrastructure에 두며 domain은 Java record를 유지합니다.
common에는 feature Entity 스캔/seed 업무 로직을 추가하지 않습니다. Boot가 root package 아래를 스캔합니다.
ArchUnit은 이전 의존 방향을 유지하고 Entity가 API DTO로 새어 나가지 않도록 경계를 검사합니다.
[12단계 검증/실행](learning/12-postgresql-jpa-flyway.md)을 참고하세요.
