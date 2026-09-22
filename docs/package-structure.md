# 패키지 구조와 설정 책임

Java 루트 패키지는 Spring Boot 실행 클래스만 둡니다. 실행 클래스가 상위 패키지에
있으므로 하위의 설정, Controller, Service, Filter는 기존 component scan으로 검색됩니다.
application YAML은 각 모듈의 src/main/resources에 유지합니다.

| 패키지 | 책임 |
|---|---|
| config/properties | `@ConfigurationProperties`가 붙은 설정 record, 값 검증, 민감값 `toString` 마스킹 |
| config/security | SecurityConfig(인가 체인), JwtConfig(키/decoder/encoder), Gateway CorsConfig |
| config/runtime | lifecycle profile과 운영 환경의 위험 설정 검증 |
| config/gateway | Gateway 사용자별 rate-limit key resolver |
| filter | Gateway 라우팅 단계의 인증 사용자 헤더 전달 |
| common/web | 라우팅 전 Request ID 정규화와 요청 로그, HTTP 헤더 상수 |
| common/error | ControllerAdvice, 오류 코드, Security writer, Backend `ApiErrorController`, Gateway `GatewayErrorHandler`/`GatewayErrorResponses`/`ProblemResponseWriter` |
| common/api | `ApiResponse` 본문, `SuccessCode` 정책, `ApiResponses.success`/`fail` HTTP 응답 생성 |
| auth/controller, auth/service, auth/dto | Gateway 인증 HTTP 경계, 토큰 발급 use case, DTO |
| controller, service, dto | Backend HTTP 경계, use case, DTO |

HTTP 진입점은 controller, 업무 로직과 transaction 경계는 service로 이름을 통일합니다.
새 업무는 기능별 패키지 아래 controller/service/dto를 배치합니다.
common/api는 Controller가 아니라 공통 응답 계약이므로 이름을 유지합니다.
Service 인터페이스와 Impl은 구현 교체 등 실제 필요가 있을 때만 분리합니다.

설정 클래스는 직접 Bean 메서드를 호출하지 않으므로 proxyBeanMethods=false를 사용합니다.
설정 클래스와 내부 guard는 package-private이며, 다른 패키지가 참조해야 하는
타입만 public으로 공개합니다. 주입은 생성자 또는 Bean 메서드
매개변수를 사용합니다. 애플리케이션 계층에서 Filter 클래스의 상수를 참조하지 않습니다.

공통화는 각 서비스 내부에서 수행합니다. Gateway는 WebFlux, Backend는 Servlet
기반이며 서로 독립적으로 빌드하므로 공통 Java 모듈을 새로 만들지 않았습니다.
두 서비스가 공유하는 HTTP 응답 계약은 api-contract.md로 관리합니다.

## 설정 객체와 애너테이션

`@ConfigurationProperties`는 설정 접두사와 Java 타입을 연결하는 애너테이션입니다.
`ConfigurationProperties`라는 프로젝트 설정 파일이나 객체로 값이 전달되는 것은 아닙니다.

| 구분 | 현재 코드의 역할 |
|---|---|
| 설정 원본 | Compose의 `environment` 또는 Kubernetes Deployment의 `envFrom`/`env`가 컨테이너 환경변수를 전달 |
| `application.yml` / `application-{profile}.yml` | 공통값과 profile별 값을 정의하며 `${...}`로 외부 값을 참조 |
| Spring `Environment` | 환경변수·설정 파일 등 여러 설정 소스를 우선순위에 따라 조회하고 플레이스홀더 해석에 사용 |
| `@ConfigurationProperties` | 접두사 아래의 값을 설정 객체에 타입 변환하여 바인딩 |
| `@EnableConfigurationProperties` | 각 모듈의 `SecurityConfig`에서 설정 타입을 Spring Bean으로 등록 |
| `@Validated`, `@Valid`, 검증 제약 및 record 생성자 | 필수값·길이·TTL·CORS 등 해당 설정의 조건을 검증. `@ConfigurationProperties`만으로 모든 조건이 검증되지는 않음 |
| `RuntimeProfileGuard` | 별도 Bean의 `@PostConstruct`에서 `Environment`를 직접 조회해 profile과 금지 설정 조합을 검사 |

실제 바인딩 대상은 Gateway의 [SecurityProperties](../gateway/src/main/java/com/example/gateway/config/properties/SecurityProperties.java)
(`app.security`), Backend의 [JwtProperties](../backend/src/main/java/com/example/backend/config/properties/JwtProperties.java)
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
- 예상하지 못한 오류는 requestId와 예외 타입을 남기며, 비밀값이 들어갈 수 있는 원문
  메시지/stack trace는 기본 로그에 쓰지 않습니다.
- JWT 키, 데모 비밀번호, 토큰 DTO의 toString은 민감값을 가립니다.
- 토큰 발급 응답은 Cache-Control: no-store, Pragma: no-cache를 보냅니다.
- 인증은 stateless로 유지하며, 인증 전 요청을 세션에 저장하는 request cache도 비활성화합니다.
- CORS는 경로/와일드카드가 없는 정확한 HTTP(S) Origin만 받습니다.
- JWT TTL은 1초 이상의 정수 초만 허용합니다.
- staging/prod에서 데모 토큰 발급을 켜면 시작에 실패합니다. 발급 Controller,
  Service, encoder에도 local/dev/test Profile 제한이 있습니다.
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

이번 변경은 경로/패키지/import 정적 점검을 수행했습니다. 설정 보호, 민감값 마스킹,
405 응답 보존, Security JSON 직렬화에 대한 회귀 테스트를 추가했지만 사용자의
요청에 따라 컴파일/테스트 및 컨테이너 재기동은 실행하지 않았습니다.
