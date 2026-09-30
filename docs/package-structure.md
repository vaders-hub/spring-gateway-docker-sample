# Feature-based 구조와 확장 기준

현재 기준은 [17단계 AA 구조 보완](learning/17-reference-architecture.md)이다. 과거 단계의 테스트 수는 당시 기록이다.

## 기본형과 확장형

작은 CRUD는 `feature/api`, `feature/application`, `feature/infrastructure`부터 시작한다.
Controller의 DTO/Mapper는 api에, Service는 application에, JPA/외부 클라이언트는 infrastructure에 둔다.
실제 업무 규칙이 생기면 domain을 추가한다. 빈 패키지, 단순 전달용 인터페이스/Impl, 동일 모양 DTO를 의무적으로 만들지 않는다.

현재 member/product/order는 저장소 교체와 기능 간 연동을 배우는 **확장형 예제**이다.
`contract`는 다른 feature에 공개하는 조회 계약, `application/port`는 호출자가 요구하는 계약,
`infrastructure/integration`은 상대 contract와 자기 port를 연결한다.
단순 조회 하나를 추가할 때 이 모든 파일을 복제할 필요는 없다.

```text
backend / com.example.backend
├── common
│   ├── config         SecurityConfig, JwtConfig, TimeConfig, MappingConfig
│   │   ├── properties JwtProperties, ObservabilityProperties
│   │   └── runtime    lifecycle 및 공개 문서 설정 검증
│   ├── exception      GlobalExceptionHandler, ApiErrorController (Servlet)
│   ├── security       FeatureRoutes, SecurityProblemWriter, permission, token
│   └── web            RequestContext, RequestIdFilter
├── hello
│   ├── api            HelloController, HelloRoutes, dto
│   └── application    HelloService
├── member             product도 동일 경계
│   ├── api            MemberController, MemberRoutes, MemberMapper, RequireMemberAdmin, dto
│   ├── application    MemberService, error/MemberErrorCode, port/MemberRepository
│   ├── contract       MemberLookup: boolean exists
│   ├── domain         Member
│   └── infrastructure fixture / persistence
└── order
    ├── api            OrderController (견적·생성·조회), OrderRoutes, OrderMapper, dto
    ├── application    OrderQuoteService (카탈로그 조회·참조 검증)
    │   ├── command    PlaceOrderService
    │   ├── query      OrderQueryService
    │   ├── error      OrderErrorCode
    │   └── port       OrderCatalog, OrderRepository
    ├── domain         OrderQuote (금액 계산·불변식), StoredOrder
    └── infrastructure fixture / persistence / integration

gateway / com.example.gateway
├── common             WebFlux 보안·라우팅·오류 writer·JWT decoder·Redis 구현
└── auth               api / application / infrastructure (데모 발급 전용)

libs/platform-core / com.example.platform
├── code               ErrorCode 인터페이스, CommonErrorCode, SuccessCode
├── response           ApiResponse, ApiResponses
├── exception          BusinessException, ProblemDetails
├── security           JwtAuthorities, token/TokenKey
└── util               ErrorDiagnostics
```

## 의존 방향과 책임

- api → application → domain. application은 api/infrastructure를 참조하지 않는다.
- domain은 Java와 **자기 feature의 domain**만 사용한다. HTTP 상태/응답은 application의 오류 코드가 소유한다.
- 다른 feature는 공개 contract만 사용하며 연결은 호출자의 infrastructure/integration에 둔다. 순환 의존은 금지한다.
- common과 platform-core는 feature에 의존하지 않는다. 공통 설정에 기능 URL 문자열도 넣지 않는다.
- 플랫폼 모듈은 공통 응답과 프로토콜만 공유한다. MVC와 WebFlux 처리기, Redis I/O 구현은 각각 유지한다.
- 업무 Controller는 `ApiResponses`, 오류 처리기는 `ProblemDetails`를 사용한다. Actuator/Prometheus를 envelope로 감싸지 않는다.
- 새로운 업무 오류는 해당 feature의 `application/error` enum이 `ErrorCode`를 구현한다. `BusinessException`에 전달하면 공통 처리기가 Problem Details로 변환한다.
- enum/util은 역할별로 묶는다. feature 전용 enum을 플랫폼이나 common에 이동하지 않는다.
- 패키지는 단수 이름을 유지한다. 하위 책임이 생기면 query/command/persistence 등으로 세분화하고, 독립 업무가 생길 때 새 feature를 만든다.

## 기능 로딩과 저장소 선택

Controller/Service/기능 간 adapter는 항상 등록한다. 저장소 adapter만 프로필로 선택한다.

| lifecycle | 추가 profile | 저장소 | 업무 API |
|---|---|---|---|
| local / test | 없음 | fixture (주문은 메모리 저장) | 조회·견적·생성·본인 조회 모두 제공 |
| local / test | persistence | PostgreSQL/JPA | 동일 |
| dev / staging / prod | 없음 | PostgreSQL/JPA | 동일 |

JPA 환경에는 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`가 필요하다. 누락 시 시작 실패가 올바른 동작이다.
fixture는 재시작 시 주문이 사라지고 여러 인스턴스 간 공유되지 않는다. 실제 stateless/내구성 실습은 persistence로 수행한다.
`app.learning.mock-enabled`, `persistence-enabled`, `ConditionalOnLearning*`는 제거했다.
공개 Swagger/OpenAPI는 기존대로 local/test의 `app.learning.docs-enabled`로 제한한다.

## 새 기능 추가

1. 필요한 api/application 코드부터 추가한다. DTO는 HTTP 입력 형식을 검증한다.
2. 기능 api 패키지의 Configuration에서 `FeatureRoutes` Bean으로 경로를 등록한다.
3. 각 HTTP handler에 `@RequireRead`, `@RequireWrite` 또는 feature 소유 `@PreAuthorize`를 선언한다.
4. Backend SecurityConfig는 등록 경로의 인증만 요구하고 나머지는 거부한다. 메서드 권한 누락은 ArchUnit 테스트로 차단한다.
5. 소유권·재고·거래 상태 등 데이터 정책은 application/domain에 둔다. Repository 조회에서도 소유자 범위를 제한한다.
6. 새 비표준 외부 API 영역을 공개하면 Gateway 라우트/상위 권한 정책도 검토한다. 기존 `/api/**` 아래 기능은 Gateway URI 목록 추가가 필요 없다.

JWT 인증 → MVC 인자 바인딩/DTO 검증 → 메서드 권한 → 업무 로직 순서다.
Backend 직접 호출에서 잘못된 DTO는 scope 부족보다 먼저 400이 될 수 있다. Gateway는 여전히 HTTP 메서드별 coarse scope를 먼저 검사한다.
`RequireMemberAdmin`은 Controller 전용이므로 member/api에 둔다.

## 설정과 시간

환경변수 → application.yml 플레이스홀더 → Spring Environment → `@ConfigurationProperties`로 실제 record에 바인딩한다.
Gateway는 `SecurityProperties`, Backend는 `JwtProperties`, 관측 설정은 `ObservabilityProperties`가 받는다.
`@EnableConfigurationProperties`는 Bean 등록, 검증 애너테이션은 값 검증을 담당한다.
`server.*`, `spring.*`는 프레임워크 설정이며 모두 위 record를 거치는 것은 아니다.
상세 흐름은 [01단계](learning/01-setup-and-compose.md)를 참고한다.

TimeConfig의 UTC Clock을 HelloService/TokenService/PlaceOrderService에 주입한다.
업무 시각은 테스트에서 고정할 수 있다. 응답 meta.timestamp는 응답 생성 시각으로 Instant.now를 사용한다.

## Gradle / Docker

루트 settings.gradle 하나가 gateway/backend/libs:platform-core를 정의한다.
버전은 gradle/libs.versions.toml에 모으고, 프레임워크 전이 의존성은 Boot/Cloud BOM이 정렬한다.
모듈별 settings.gradle은 제거했다. 모든 명령은 저장소 루트에서 실행한다.

```bash
bash ./gradlew :libs:platform-core:test :gateway:test :backend:test
bash ./gradlew :backend:databaseTest
bash ./gradlew :backend:bootJar :gateway:bootJar
docker build -f backend/Dockerfile -t local-backend:dev .
docker build -f gateway/Dockerfile -t local-gateway:dev .
```

Docker는 루트 context에서 공유 모듈과 대상 서비스만 컴파일한다. 실행 이미지는 서비스 JAR와 JRE만 포함한다.
두 서비스는 따로 배포할 수 있지만 공통 계약 변경은 함께 검증해야 한다. 호환성이 깨지는 프로토콜 변경에는 버전 전환 계획이 필요하다.

## 검증 범위

Backend ArchUnit은 핵심 규칙만 검사한다: application → api/infrastructure 참조 금지, feature 간 순환 금지,
HTTP handler 권한 누락 금지. 그 밖의 경계(domain, contract/integration, common 의존)는 위 원칙과 코드 리뷰로 유지한다.
구조 규칙을 임시로 끄려면 `bash ./gradlew test -PskipArchitecture`로 실행한다(`architecture` 태그 제외). 통합 테스트는 기능 api 및 infrastructure/persistence 패키지에 배치한다.
DB 테스트는 **prod 프로필 + 일회용 PostgreSQL/Redis**로 운영 설정의 API 등록·저장·rollback을 검증한다. 실제 운영 배포 검증은 아니다.
Spring Modulith 도입은 보류한다. 현재 경계를 검증하는 데 필요한 규칙이 이미 있으며, 추가 모듈 이벤트/문서화 요구가 생길 때 Boot 호환 버전을 확인한다.
