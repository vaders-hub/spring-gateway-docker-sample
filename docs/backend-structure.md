# Backend 구조와 파일별 기능

Spring MVC(Servlet/Tomcat) 기반 업무 서비스다. Gateway를 거쳐 들어온 요청의 JWT를 다시 검증하고, 회원·상품·주문 업무를 처리한다. 저장소는 프로필에 따라 메모리, JPA, MyBatis 중 하나를 쓴다.

- 모듈 경로: `backend/`
- 루트 패키지: `com.example.backend`
- 포트: `8081` (Compose에서 호스트에 공개하지 않음. 학습용 `docker-compose.learning.yml`로만 `18081` 공개)
- 웹 스택: Spring MVC + Spring Security(OAuth2 Resource Server) + 메서드 보안
- 함께 보는 문서: [gateway-structure.md](gateway-structure.md), [package-structure.md](package-structure.md), [persistence-policy.md](persistence-policy.md), [api-contract.md](api-contract.md)

## 1. 요청 처리 흐름

```text
Gateway  (/api/orders → StripPrefix → /orders, X-Request-Id 전달)
  ▼
RequestIdFilter                 요청 ID 확정, 요청 로그(http_request)
  ▼
Spring Security (SecurityConfig)
  ├─ JWT 재검증 (JwtConfig): 서명·issuer·audience·만료·sub, 데모 모드는 Redis 활성 토큰 확인
  ├─ FeatureRoutes에 등록된 경로만 "인증 필요"로 열고 나머지는 모두 거부
  └─ 401·403은 SecurityProblemWriter가 Problem Details로 응답
  ▼
Controller (기능/web)
  ├─ @Valid DTO 검증 → 실패 시 400
  └─ @RequireRead / @RequireWrite / @RequireMemberAdmin 메서드 권한 → 부족 시 403
  ▼
Service 인터페이스 (기능/service) → ServiceImpl (기능/service/impl)
  ▼
Repository 인터페이스 (기능/repository)
  ├─ Memory*Repository          local/test 기본
  ├─ *RepositoryJpaImpl         dev/staging/prod 기본, 또는 persistence 프로필
  └─ *RepositoryMyBatisImpl     mybatis 프로필
```

URL 경로는 Gateway가 `/api`를 떼고 전달한 경로다. 클라이언트 기준 `GET /api/products`는 Backend의 `GET /products`다.

## 2. API 목록

| 메서드·경로 (Backend 기준) | 권한 | Controller | 설명 |
|---|---|---|---|
| `GET /hello` | api.read | `HelloController` | 연결 확인용 인사 응답 |
| `POST /echo` | api.write | `HelloController` | 입력 검증 학습용 에코 |
| `GET /members` | api.read | `MemberController` | 회원 목록 |
| `GET /members/admin` | api.read + member.admin | `MemberController` | 관리자 회원 목록 |
| `GET /members/{id}` | api.read | `MemberController` | 회원 단건 (없으면 404) |
| `GET /products` | api.read | `ProductController` | 상품 목록 |
| `GET /products/{id}` | api.read | `ProductController` | 상품 단건 (없으면 404) |
| `POST /orders/preview` | api.write | `OrderController` | 주문 견적 계산 (저장 안 함, 200) |
| `POST /orders` | api.write | `OrderController` | 주문 생성 (201) |
| `GET /orders/{id}` | api.read | `OrderController` | 본인 주문 조회 |
| `PATCH /orders/{id}` | api.write | `OrderController` | 본인 주문 수량 수정 (주문 당시 단가 유지) |
| `DELETE /orders/{id}` | api.write | `OrderController` | 본인 주문 삭제 |
| `GET /orders` | api.read | `OrderSearchController` | 본인 주문 조건 검색·페이징 (**mybatis 프로필에서만 존재**) |

`GET /orders`는 Controller·서비스 구현·저장소 구현 모두 `@Profile("mybatis")`로 제한된다. 주문 생성·단건 조회·수량 수정·삭제는 메모리·JPA·MyBatis에서 공통으로 제공한다.

다른 사용자의 주문은 존재 여부를 숨기기 위해 모두 404(`ORDER_NOT_FOUND`)로 응답한다. 주문 생성·견적에서 없는 회원·상품을 지정하면 404가 아니라 422(`INVALID_MEMBER_REFERENCE`, `INVALID_PRODUCT_REFERENCE`)다.

## 3. 패키지 구조

```text
com.example.backend
├── BackendApplication.java
├── common                         기능과 무관한 공통 코드 (기능 패키지를 참조하지 않음)
│   ├── config                     Security, JWT, MapStruct, MyBatis, Kafka, OpenAPI, 시계
│   │   ├── properties             JwtProperties, ObservabilityProperties, KafkaMessagingProperties
│   │   └── runtime                RuntimeProfileGuard, LearningProfileGuard
│   ├── exception                  GlobalExceptionHandler, ApiErrorController
│   ├── persistence/mybatis        UuidTypeHandler
│   ├── security                   FeatureRoutes, SecurityProblemWriter
│   │   ├── permission             RequireRead, RequireWrite
│   │   └── token                  ActiveTokenStore, TokenStoreUnavailableException
│   └── web                        RequestContext, RequestIdFilter
├── hello                          연결 확인 예제 (저장소 없음)
│   ├── service / service/impl
│   └── web / web/dto
├── member                         회원 조회
├── product                        상품 조회
└── order                          주문 견적·생성·조회·수정·삭제·검색
    ├── error                      기능 오류 코드 (ErrorCode enum)
    ├── model                      업무 객체 (record, 불변)
    ├── messaging                  주문 이벤트 계약·Kafka producer/consumer (선택)
    ├── repository                 저장소 인터페이스 + 메모리 구현
    │   ├── jpa                    Entity, Spring Data 인터페이스, JPA 구현
    │   └── mybatis                @Mapper 인터페이스, MyBatis 구현
    ├── service                    공개 Service 인터페이스 (다른 기능은 여기만 사용)
    │   └── impl                   ServiceImpl
    └── web                        Controller, Routes, DtoConverter
        └── dto                    요청·응답 DTO
```

기능 패키지(`member`, `product`, `order`)는 같은 배치를 따른다. 각 하위 패키지의 역할:

| 하위 패키지 | 역할 | 넣지 않는 것 |
|---|---|---|
| `web` | HTTP 입력·권한·응답 형태. Controller, 경로 등록(`*Routes`), MapStruct 변환기(`*DtoConverter`) | 업무 규칙, SQL |
| `web/dto` | 요청·응답 record. 요청은 Bean Validation 애너테이션으로 형식 검증 | JPA Entity, 업무 객체 |
| `service` | 다른 계층·기능에 공개하는 Service 인터페이스와 공개 값 타입(예: `ProductSnapshot`) | 구현체, JPA·Spring Data 타입 |
| `service/impl` | 업무 흐름과 트랜잭션. 저장소 인터페이스와 다른 기능의 Service 인터페이스만 사용 | HTTP 타입 |
| `model` | 업무 객체와 불변식(예: 수량 1~100, 음수 가격 금지, 금액 계산) | Spring, JPA, HTTP 타입 |
| `repository` | 저장소 인터페이스와 메모리 구현 | Service, web |
| `repository/jpa` | `@Entity`, Spring Data `JpaRepository`, 저장소 인터페이스의 JPA 구현 | |
| `repository/mybatis` | MyBatis `@Mapper` 인터페이스, 저장소 인터페이스의 MyBatis 구현, SQL 행 매핑 record | |
| `messaging` | 기능 이벤트 계약과 Kafka 발행·소비. `order`에만 필요한 패키지를 추가한다. | HTTP DTO, 인증 토큰 |
| `error` | `ErrorCode`를 구현하는 enum (코드 이름, HTTP 상태, 공개 문구) | 예외 클래스 |

의존 방향 규칙(`ArchitectureTest`로 검사):

- `service`, `repository`는 `web`을 참조하지 않는다.
- `repository`는 `service`를 참조하지 않는다.
- 공개 `service` 패키지는 `service.impl`, `repository`, Spring Data, JPA 타입을 참조하지 않는다. `service` 안의 record는 Java 표준 타입과 같은 패키지 타입만 쓴다.
- 다른 기능은 상대 기능의 **`service` 패키지만** 참조한다(예: `order`는 `MemberService`, `ProductService`, `ProductSnapshot`만 사용).
- `common`과 `platform-core`는 기능 패키지를 참조하지 않는다.
- 기능 사이에 순환 의존이 없다.
- 모든 기능 Controller의 HTTP 메서드에는 권한 애너테이션(`@PreAuthorize` 계열)이 있다.

## 4. 저장소 선택 (프로필)

lifecycle 프로필(`local`, `dev`, `test`, `staging`, `prod`) 하나에, 저장소 선택 프로필을 더한다.

| 활성 프로필 | 선택되는 구현 | DB·Flyway | 비고 |
|---|---|---|---|
| `local` 또는 `test` | `Memory*Repository` | 사용 안 함 (자동 구성 제외) | 회원·상품은 고정 데이터 2건, 주문은 메모리(재시작 시 사라짐) |
| `local`/`test` + `persistence` | `*RepositoryJpaImpl` | PostgreSQL + Flyway | `persistence`는 local/test에서만 허용 |
| `dev`, `staging`, `prod` | `*RepositoryJpaImpl` | PostgreSQL + Flyway | 운영 기본 |
| 아무 lifecycle + `mybatis` | `*RepositoryMyBatisImpl` | PostgreSQL + Flyway | JPA 자동 구성 제외. `persistence`와 함께 쓸 수 없음. 주문 검색 API는 이 모드에만 있음 |

`kafka`는 저장소·`oidc`와 독립된 추가 프로필이다. 지정하지 않으면 Kafka 연결·토픽 생성·소비를 시작하지 않는다. 지정하면 주문 생성·수량 수정·삭제 이벤트를 DB 커밋 후 발행한다(메모리에서는 즉시). 소비자는 로그 처리, 제한된 재시도와 DLT를 제공한다. Outbox·영구 중복 방지와 운영 보안은 후속 범위다. [19단계](learning/19-kafka-order-events.md)에 실행 구성과 전달 보장 경계를 정리했다.

각 구현 클래스의 `@Profile` 식:

- 메모리: `(local | test) & !persistence & !mybatis`
- JPA: `((!local & !test) | persistence) & !mybatis`
- MyBatis: `mybatis`

`oidc` 프로필은 저장소와 무관한 인증 방식 선택이며 위 조합과 함께 쓸 수 있다. 잘못된 조합은 `RuntimeProfileGuard`가 기동 시 거부한다.

## 5. 파일별 기능

### 5.1 진입점

| 파일 | 기능 |
|---|---|
| `BackendApplication.java` | Spring Boot 시작 클래스. `com.example.backend` 하위를 컴포넌트 스캔한다. |

### 5.2 `common/config` — 설정

| 파일 | 기능 |
|---|---|
| `SecurityConfig.java` | Servlet Security 필터 체인과 `@EnableMethodSecurity`. 무상태(세션·CSRF·폼 로그인 끔). 인가 규칙: 오류 dispatch 허용, health·info 허용, Prometheus는 설정에 따라 공개 또는 인증, **각 기능이 등록한 `FeatureRoutes` 경로는 인증 필요**, 학습 문서 모드에서 Swagger 경로 허용, 그 외 전부 거부. 세부 scope 검사는 메서드 애너테이션이 담당한다. |
| `JwtConfig.java` | `JwtDecoder` Bean. 데모 모드는 HS256 공유 비밀키, OIDC 모드는 JWKS(RS256). issuer·audience·만료·`sub`를 검사한다. 데모 모드에서는 서명 검증 후 Redis 활성 토큰을 확인해 로그아웃된 토큰을 거부하고, Redis 장애는 503으로 구분한다. |
| `MappingConfig.java` | MapStruct 공통 설정. Spring Bean으로 생성, 생성자 주입, **매핑되지 않은 응답 필드가 있으면 컴파일 오류**(`unmappedTargetPolicy = ERROR`). |
| `MyBatisConfig.java` | `mybatis` 프로필에서만 켜지는 매퍼 스캔. `com.example.backend` 아래에서 MyBatis `@Mapper`(`org.apache.ibatis.annotations.Mapper`)가 붙은 인터페이스만 등록한다. MapStruct의 `@Mapper`와는 다른 애너테이션이라 섞이지 않는다. 새 기능은 매퍼에 `@Mapper`만 붙이면 된다. |
| `KafkaConfig.java` | `kafka` 프로필의 주문 토픽·DLT 생성, 재시도 2회·복구 설정. 공통 설정은 주문 이벤트 클래스를 참조하지 않는다. |
| `properties/KafkaMessagingProperties.java` | 토픽 이름·파티션·복제 수의 타입과 필수값 검증. |
| `OpenApiConfig.java` | `local`·`test`이고 `app.learning.docs-enabled=true`일 때만 OpenAPI 문서 정보와 Bearer 인증 스키마를 등록한다. |
| `TimeConfig.java` | UTC `Clock` Bean. 주문 생성 시각, hello 응답 시각에 주입되며 테스트에서 고정할 수 있다. |
| `properties/JwtProperties.java` | `app.security.jwt.*` 바인딩과 시작 시 검증. issuer·audience 필수, 비밀키 32자 이상 **또는** JWKS URI 중 하나만 허용. `toString()`에서 비밀키를 가린다. |
| `properties/ObservabilityProperties.java` | `app.observability.prometheus-public` 바인딩. |
| `runtime/RuntimeProfileGuard.java` | 프로필 조합 검사. lifecycle 하나만, 추가 허용은 `persistence`·`mybatis`·`oidc`·`kafka`. `persistence`와 `mybatis` 동시 사용 금지, `persistence`는 local/test에서만, local/test 외 Prometheus 공개 금지. |
| `runtime/LearningProfileGuard.java` | local/test가 아닌 환경에서 Swagger·OpenAPI 공개 설정이 켜져 있으면 기동을 막는다. 업무 API는 모든 환경에서 제공된다. |

### 5.3 `common/exception` — 오류 응답

| 파일 | 기능 |
|---|---|
| `GlobalExceptionHandler.java` | Controller 예외의 공통 Advice. `BusinessException` → 해당 `ErrorCode`의 상태와 문구, DTO 검증 실패 → 400 + 필드별 `errors`, 경로 변수·파라미터 타입 오류 → 400, 본문 누락·형식 오류 → 400, 권한 부족 → 403, 프레임워크 4xx·5xx는 상태와 헤더(`Allow` 등) 보존, 그 외 → 500 + `unhandled_request_error` 로그(예외 메시지는 남기지 않음). |
| `ApiErrorController.java` | Servlet 컨테이너의 오류 dispatch(`/error`)를 받는 Controller. Advice 밖에서 난 필터 예외나 `sendError`도 JSON Problem Details로 응답하고, 5xx는 `servlet_request_error`로 로그를 남긴다. 외부에서 `/error`를 직접 호출하면 Security가 거부한다. |

### 5.4 `common/persistence`, `common/security`, `common/web`

| 파일 | 기능 |
|---|---|
| `persistence/mybatis/UuidTypeHandler.java` | MyBatis에서 `java.util.UUID`를 PostgreSQL `UUID` 타입으로 직접 바인딩·조회한다. `type-handlers-package` 설정으로 자동 등록된다. |
| `security/FeatureRoutes.java` | 기능이 공개할 URL 경로 목록을 담는 record. 각 기능의 `*Routes` 설정 클래스가 Bean으로 등록하면 `SecurityConfig`가 모아 "인증 필요" 경로로 연다. 등록하지 않은 경로는 기본 거부된다. |
| `security/SecurityProblemWriter.java` | Security 단계의 401·403을 Problem Details JSON으로 쓴다. 원인이 토큰 저장소 장애면 503. |
| `security/permission/RequireRead.java` | `@PreAuthorize("hasAuthority('SCOPE_api.read')")` 합성 애너테이션. 조회 API에 붙인다. |
| `security/permission/RequireWrite.java` | `@PreAuthorize("hasAuthority('SCOPE_api.write')")` 합성 애너테이션. 변경 API에 붙인다. |
| `security/token/ActiveTokenStore.java` | Redis 활성 토큰 조회(동기 `StringRedisTemplate`). 키는 토큰의 SHA-256(`TokenKey.of`). Redis 오류는 `TokenStoreUnavailableException`으로 바꿔 인증을 통과시키지 않는다. Backend는 조회만 하고 쓰기는 Gateway가 한다. |
| `security/token/TokenStoreUnavailableException.java` | Redis 장애를 잘못된 토큰(401)과 구분하기 위한 예외. |
| `web/RequestContext.java` | `X-Request-Id` 헤더 이름과 요청 속성 이름 상수. |
| `web/RequestIdFilter.java` | 첫 번째 Servlet 필터. Gateway가 보낸 유효한 요청 ID(영문·숫자·`._-` 1~64자)를 유지하고, 없거나 형식이 틀리면 UUID를 만든다. 응답 헤더·요청 속성에 넣고, 끝나면 `http_request` 로그(method, path, status, 소요 시간)를 남긴다. |

### 5.5 `hello` — 연결 확인 예제

| 파일 | 기능 |
|---|---|
| `service/HelloService.java` | 인터페이스. `hello(username)`, `echo(name, value, username)`와 결과 record(`HelloResult`, `EchoResult`). |
| `service/impl/HelloServiceImpl.java` | 주입된 `Clock`으로 현재 시각을 넣어 결과를 만든다. HTTP 의존 없음. |
| `web/HelloController.java` | `GET /hello`(api.read), `POST /echo`(api.write, `@Valid`). 사용자 이름은 `X-Gateway-User` 헤더가 아니라 검증된 JWT의 Principal에서 얻는다. |
| `web/HelloRoutes.java` | `FeatureRoutes.of("/hello", "/echo")` 등록. |
| `web/dto/EchoRequest.java` | `name`(필수, 최대 100자), `value`(필수, 0~1,000,000). |
| `web/dto/EchoResponse.java` | 응답. 입력 DTO와 분리된 중첩 record `Received`. |
| `web/dto/HelloResponse.java` | 응답. `service`, `message`, `time`, `username`. |

### 5.6 `member` — 회원 조회

| 파일 | 기능 |
|---|---|
| `error/MemberErrorCode.java` | `MEMBER_NOT_FOUND`(404). |
| `model/Member.java` | 회원 업무 객체 `record(id, displayName)`. |
| `repository/MemberRepository.java` | 저장소 인터페이스. `findAll()`, `findById(id)`. |
| `repository/MemoryMemberRepository.java` | 메모리 구현. 고정 데이터 2건(Sample Member, Second Member). |
| `repository/jpa/MemberEntity.java` | `members` 테이블 매핑 Entity. `toDomain()`으로 `Member`로 바꾼다. Controller로 노출하지 않는다. |
| `repository/jpa/MemberJpaRepository.java` | Spring Data `JpaRepository<MemberEntity, Long>`. 패키지 내부 전용. |
| `repository/jpa/MemberRepositoryJpaImpl.java` | `MemberRepository`의 JPA 구현. 읽기 전용 트랜잭션 안에서 Entity → 업무 객체 변환을 끝낸다(OSIV가 꺼져 있어도 지연 로딩 문제 없음). |
| `repository/mybatis/MemberMapper.java` | MyBatis `@Mapper`. `findAll`, `findById`. SQL은 `resources/mapper/member/MemberMapper.xml`. |
| `repository/mybatis/MemberRepositoryMyBatisImpl.java` | `MemberRepository`의 MyBatis 구현. `null` 결과를 `Optional`로 감싼다. |
| `service/MemberService.java` | 공개 인터페이스. `list()`, `get(id)`(없으면 404 예외), `exists(memberId)`(다른 기능용 존재 확인, 예외 대신 boolean). |
| `service/impl/MemberServiceImpl.java` | 구현. 저장소 인터페이스에만 의존한다. |
| `web/MemberController.java` | `GET /members`, `GET /members/admin`, `GET /members/{id}`(양수 검증). |
| `web/MemberDtoConverter.java` | MapStruct 변환기. `Member` → `MemberResponse`. 구현은 컴파일 시 생성된다. |
| `web/MemberRoutes.java` | `FeatureRoutes.of("/members", "/members/**")` 등록. |
| `web/RequireMemberAdmin.java` | 회원 기능 전용 권한 애너테이션. `SCOPE_api.read`와 `SCOPE_member.admin`을 모두 요구한다. |
| `web/dto/MemberResponse.java` | 응답 record `(id, displayName)`. |

### 5.7 `product` — 상품 조회

| 파일 | 기능 |
|---|---|
| `error/ProductErrorCode.java` | `PRODUCT_NOT_FOUND`(404). |
| `model/Product.java` | 상품 업무 객체 `record(id, name, unitPrice, currency)`. 금액은 `BigDecimal`. |
| `repository/ProductRepository.java` | 저장소 인터페이스. `findAll()`, `findById(id)`. |
| `repository/MemoryProductRepository.java` | 메모리 구현. 고정 데이터 2건(Keyboard 50,000원, Mouse 20,000원). |
| `repository/jpa/ProductEntity.java` | `products` 테이블 매핑 Entity. |
| `repository/jpa/ProductJpaRepository.java` | Spring Data `JpaRepository<ProductEntity, Long>`. |
| `repository/jpa/ProductRepositoryJpaImpl.java` | `ProductRepository`의 JPA 구현(읽기 전용 트랜잭션, id 순 정렬). |
| `repository/mybatis/ProductMapper.java` | MyBatis `@Mapper`. SQL은 `resources/mapper/product/ProductMapper.xml`. |
| `repository/mybatis/ProductRepositoryMyBatisImpl.java` | `ProductRepository`의 MyBatis 구현. |
| `service/ProductService.java` | 공개 인터페이스. `list()`, `get(id)`(없으면 404), `findProduct(productId)`(다른 기능용, `Optional<ProductSnapshot>`). |
| `service/ProductSnapshot.java` | 다른 기능에 공개하는 상품 값 `record(id, name, unitPrice, currency)`. 내부 `Product`나 Entity를 노출하지 않기 위한 타입이다. |
| `service/impl/ProductServiceImpl.java` | 구현. `findProduct`는 `Product`를 `ProductSnapshot`으로 바꿔 반환한다. |
| `web/ProductController.java` | `GET /products`, `GET /products/{id}`. |
| `web/ProductDtoConverter.java` | MapStruct 변환기. `Product` → `ProductResponse`. |
| `web/ProductRoutes.java` | `FeatureRoutes.of("/products", "/products/**")` 등록. |
| `web/dto/ProductResponse.java` | 응답 record. |

### 5.8 `order` — 주문

| 파일 | 기능 |
|---|---|
| `error/OrderErrorCode.java` | `ORDER_NOT_FOUND`(404), `INVALID_MEMBER_REFERENCE`(422), `INVALID_PRODUCT_REFERENCE`(422). |
| `model/OrderQuote.java` | 견적. 회원·상품 ID 양수, 수량 1~100, 단가 0 이상을 생성자에서 검사하고 `totalPrice()`(단가 × 수량)를 `BigDecimal`로 계산한다. |
| `model/StoredOrder.java` | 저장된 주문 `(id, ownerSubject, quote, createdAt)`. 주문 당시 상품명·단가를 고정한다. `withQuantity()`는 수량만 바꾼 새 객체를 만든다. |
| `model/OrderSearchCriteria.java` | 검색 조건 `(memberId, productName, minimumTotal, page, size)`. 범위 검사(page 0~10000, size 1~100 등)와 상품명 공백 정리, `offset()` 계산. |
| `model/OrderSearchResult.java` | 검색 결과 `(items, total, page, size)`. |
| `model/OrderSummary.java` | 검색 결과 한 줄. JOIN 결과로 회원명, 주문 당시 상품명과 현재 상품명, 총액을 함께 담는다. |
| `repository/OrderRepository.java` | 저장소 인터페이스. `save`, `findByIdAndOwnerSubject`, `updateQuantityByIdAndOwnerSubject`, `deleteByIdAndOwnerSubject`. 모든 조회·변경에 소유자 조건이 들어간다. |
| `repository/OrderSearchRepository.java` | 검색 전용 저장소 인터페이스. `search`, `count`. 현재 구현은 MyBatis뿐이다. |
| `repository/MemoryOrderRepository.java` | 메모리 구현(`ConcurrentHashMap`). 다른 소유자의 주문은 수정·삭제되지 않는다. |
| `repository/jpa/OrderEntity.java` | `purchase_orders` 테이블 매핑 Entity. 회원·상품은 JPA 연관관계 없이 ID로만 저장하고, 무결성은 DB FK가 보장한다. |
| `repository/jpa/OrderJpaRepository.java` | Spring Data 인터페이스. 소유자 조건 조회, JPQL `update`·`delete`(`@Modifying`, 실행 전후 영속성 컨텍스트 정리). |
| `repository/jpa/OrderRepositoryJpaImpl.java` | `OrderRepository`의 JPA 구현. 저장은 `saveAndFlush`로 즉시 SQL을 실행하고, 수정·삭제는 영향 행이 1일 때만 성공으로 본다. |
| `repository/mybatis/OrderMapper.java` | MyBatis `@Mapper`. `insert`, `updateQuantityByIdAndOwnerSubject`, `deleteByIdAndOwnerSubject`, `findByIdAndOwnerSubject`, `search`, `count`. |
| `repository/mybatis/OrderRow.java` | SQL 결과의 평평한 행 record. `from(StoredOrder)`, `toDomain()`으로 중첩 업무 객체와 오간다. |
| `repository/mybatis/OrderRepositoryMyBatisImpl.java` | `OrderRepository`의 MyBatis 구현. INSERT 영향 행이 1이 아니면 예외. commit·rollback은 Service 트랜잭션에 맡긴다. |
| `repository/mybatis/OrderSearchRepositoryMyBatisImpl.java` | `OrderSearchRepository`의 MyBatis 구현(`mybatis` 프로필). |
| `service/OrderService.java` | 공개 인터페이스. `preview`, `place`, `get`, `updateQuantity`, `delete`. |
| `service/OrderSearchService.java` | 검색 인터페이스. `search(criteria, ownerSubject)`. |
| `service/impl/OrderServiceImpl.java` | 주문 업무. `preview`: `MemberService.exists`, `ProductService.findProduct`로 참조를 확인하고(없으면 422) 서버 가격으로 견적. `place`(`@Transactional`): 소유자 검증 → 견적 → UUID·현재 시각으로 저장. `updateQuantity`(`@Transactional`): 수량 1~100 확인, 재견적 없이 수량만 변경, 없으면 404. `delete`(`@Transactional`): 없으면 404. `get`(읽기 전용): 소유자 조건 조회, 없으면 404. |
| `service/impl/OrderSearchServiceImpl.java` | 검색(`mybatis` 프로필). `count`와 `search`를 같은 읽기 전용·`REPEATABLE_READ` 트랜잭션에서 실행해 total과 목록이 같은 시점 데이터를 보게 한다. |
| `web/OrderController.java` | `POST /orders/preview`, `POST /orders`(201), `GET /orders/{id}`, `PATCH /orders/{id}`, `DELETE /orders/{id}`. 소유자는 요청 본문이 아니라 JWT Principal에서 얻는다. |
| `web/OrderSearchController.java` | `GET /orders` 검색(`mybatis` 프로필에서만 등록). 조건은 쿼리 파라미터(`@ModelAttribute`)로 받는다. |
| `web/OrderDtoConverter.java` | MapStruct 변환기. `OrderQuote` → `OrderPreviewResponse`(총액 포함), `StoredOrder` → `OrderResponse`(소유자 미노출), 검색 결과 변환. |
| `web/OrderRoutes.java` | `FeatureRoutes.of("/orders", "/orders/**")` 등록. |
| `web/dto/request/OrderPreviewRequest.java` | 견적 요청. `memberId`, `productId`(양수), `quantity`(1~100). |
| `web/dto/request/OrderCreateRequest.java` | 생성 요청. 견적 요청과 같은 필드. 소유자·가격·총액은 받지 않는다. |
| `web/dto/request/OrderUpdateRequest.java` | 수정 요청. `quantity`(1~100)만. |
| `web/dto/request/OrderSearchRequest.java` | 검색 요청. `memberId`, `productName`(최대 100자), `minimumTotal`(0 이상), `page`(기본 0), `size`(기본 20, 최대 100). `toCriteria()`로 업무 조건으로 바꾼다. |
| `web/dto/response/OrderPreviewResponse.java` | 견적 응답(단가, 총액, 통화 포함). |
| `web/dto/response/OrderResponse.java` | 주문 응답 `(id, quote, createdAt)`. |
| `web/dto/response/OrderDeleteResponse.java` | 삭제 응답 `(id, deleted)`. |
| `web/dto/response/OrderSearchItemResponse.java` | 검색 결과 한 줄 응답. |
| `web/dto/response/OrderSearchResponse.java` | 검색 응답 `(items, total, page, size)`. |

## 6. 리소스와 설정 파일 (`src/main/resources`)

### 6.1 Spring 설정

| 파일 | 내용 |
|---|---|
| `application.yml` | 공통. 포트 8081, graceful shutdown, Tomcat 스레드·연결 제한, Redis 연결, `app.security.jwt.*`, 학습 문서 기본 꺼짐, Actuator(health/info/prometheus, liveness·readiness 분리, readiness에 Redis), 지표 histogram, tracing, Swagger 대상 경로. 아래에 프로필별 문서 3개가 이어진다: ① 메모리 모드에서 DataSource·JPA·Flyway·MyBatis 자동 구성 제외, ② DB 모드(`persistence`, `mybatis`, 운영)에서 DataSource·Hikari·JPA(`open-in-view: false`, `ddl-auto: validate`)·Flyway 설정과 readiness에 DB 추가, ③ JPA 모드에서 MyBatis 자동 구성 제외. |
| `application-local.yml` | tracing 100%, `com.example.backend` DEBUG 로그(MyBatis SQL도 출력), Swagger 학습 문서 켬. |
| `application-dev.yml` | tracing 100%, DEBUG 로그. |
| `application-test.yml` | OTLP 지표·tracing export 끔. |
| `application-staging.yml` | INFO 로그, ECS JSON 구조화 로그. |
| `application-prod.yml` | INFO 로그, ECS JSON 구조화 로그. |
| `application-persistence.yml` | 내용 없음(주석만). 프로필 이름으로 JPA 구현을 고르는 용도이며 DB 설정은 `application.yml`에 있다. |
| `application-mybatis.yml` | JPA 자동 구성 제외, 매퍼 XML 위치(`classpath*:mapper/**/*.xml`), 타입 핸들러 패키지, 문장 타임아웃 3초, 로컬 캐시 범위 STATEMENT, 생성자 인자 이름 기반 매핑. |
| `application-oidc.yml` | Keycloak issuer·JWKS URI, 공유 비밀키 비움, readiness 구성을 환경변수로 지정. |
| `application-kafka.yml` | 선택 Kafka 연결·producer/consumer·토픽 설정. `order/messaging`에서 생성·수정·삭제 이벤트를 발행·소비한다. |

### 6.2 DB와 SQL

Kafka 설정은 `application-kafka.yml`에 있다. bootstrap 서버·consumer group·토픽·partition·replica를 지정하고, String JSON·producer idempotence·record ack를 사용한다. `common/config/KafkaConfig`는 토픽과 DLT를 생성하고 오류 재시도를 설정한다. 이벤트 계약 및 producer/consumer는 `order/messaging`에 있다.

| 파일 | 내용 |
|---|---|
| `db/migration/V1__create_learning_catalog_and_orders.sql` | Flyway 초기 스키마. `members`, `products`(가격 0 이상, 통화 3자리 대문자 CHECK), `purchase_orders`(UUID PK, 소유자, 회원·상품 FK, 주문 당시 상품명·단가, 수량 1~100 CHECK, 생성 시각) 및 FK 인덱스. 예제 데이터는 넣지 않는다. |
| `mapper/member/MemberMapper.xml` | `findAll`(id 순), `findById`. 생성자 매핑으로 `Member` record를 만든다. |
| `mapper/product/ProductMapper.xml` | `findAll`(id 순), `findById`. |
| `mapper/order/OrderMapper.xml` | `insert`, `updateQuantityByIdAndOwnerSubject`, `deleteByIdAndOwnerSubject`, `findByIdAndOwnerSubject`, 검색용 공통 `<sql>` 조각(회원·상품 JOIN, 소유자 조건 고정, 회원 ID·상품명·최소 총액 선택 조건), `search`(최신순 + id 정렬, `LIMIT`/`OFFSET`), `count`. |

## 7. 공통 모듈 (`libs/platform-core`)

Gateway와 Backend가 함께 쓰는 라이브러리다(`com.example.platform`). 웹 스택(MVC/WebFlux)이나 Redis 구현은 넣지 않고, 응답·오류·인증 **계약**만 둔다.

| 파일 | 기능 |
|---|---|
| `code/ErrorCode.java` | 오류 코드 인터페이스. `code()`, `status()`, `title()`, `detail()`. 기능별 오류 enum이 구현한다. |
| `code/CommonErrorCode.java` | 공통 오류 코드 10종(401, 403, 400, 404, 405, 429, 502, 503, 504, 500). `fromStatus(int)`로 HTTP 상태에서 코드를 찾는다. |
| `code/SuccessCode.java` | 성공 종류별 HTTP 상태와 캐시 금지 여부. `OK`, `CREATED`, `TOKEN_ISSUED`(no-store), `LOGGED_OUT`(no-store). |
| `exception/BusinessException.java` | 업무 예외. `ErrorCode`만 담고 입력값이나 내부 메시지는 담지 않는다. 공통 Advice가 Problem Details로 바꾼다. |
| `exception/ProblemDetails.java` | Problem Details 응답의 단일 생성 지점. `type`(`urn:problem:<code>`), `title`, `detail`, `instance`(경로), `errorCode`, `requestId`를 채우고 `application/problem+json`, `Cache-Control: no-store`, 401이면 `WWW-Authenticate: Bearer`를 붙인다. |
| `response/ApiResponse.java` | 성공 응답 본문 `{ data, meta: { requestId, timestamp } }`. |
| `response/ApiResponses.java` | 성공 응답 생성 도구 `success(code, data, requestId)`. no-store 코드면 캐시 금지 헤더를 붙인다. |
| `security/JwtAuthorities.java` | JWT 클레임 → Spring 권한 변환기. 데모 모드는 `scope`, OIDC 모드는 Keycloak이 계산한 `permissions` 클레임을 쓴다(클라이언트가 요청한 scope로 권한 상승 방지). |
| `security/token/TokenKey.java` | 활성 토큰 Redis 키 규칙 `auth:active:v1:<SHA-256>`. Gateway(쓰기)와 Backend(읽기)가 같은 규칙을 쓴다. |
| `util/ErrorDiagnostics.java` | 오류 로그용 원인 요약. 각 cause의 타입과 첫 발생 위치만 최대 8개 남기고 예외 메시지는 남기지 않는다(비밀값 노출 방지). |

## 8. 빌드

| 파일 | 내용 |
|---|---|
| `backend/build.gradle` | Java 25 toolchain. 의존성: `platform-core`, Spring Web, Actuator, Security, Redis, OAuth2 Resource Server, Validation, Data JPA, Flyway(PostgreSQL), MyBatis·Kafka Boot Starter, MapStruct, springdoc-openapi, OpenTelemetry, Prometheus, Testcontainers(PostgreSQL·Kafka), ArchUnit. 산출물 `app.jar`. 기본 `test`는 `database`·`kafka` 태그를 제외하고 Docker 없이 실행되며, `-PskipArchitecture`로 `architecture` 태그도 제외할 수 있다. `databaseTest`·`kafkaTest`는 각각 해당 태그만 실행한다. |
| `backend/Dockerfile` | 멀티스테이지 빌드. `gradle:9.7.1-jdk25`에서 `:backend:bootJar` → `eclipse-temurin:25-jre`. UID 10001 비루트 사용자. |

## 9. 테스트 (`src/test`)

Kafka 의존성은 Boot BOM이 관리하는 `spring-boot-starter-kafka`를 사용한다. `backend:kafkaTest`는 `kafka` 태그의 테스트만 실행하며 Testcontainers Kafka 모듈이 일회용 브로커를 제공한다. 기본 `test`는 `database`·`kafka` 태그를 모두 제외한다.

| 파일 | 검증 내용 |
|---|---|
| `architecture/ArchitectureTest` | 3장의 패키지 의존 규칙 7개. `@Tag("architecture")`. |
| `common/config/properties/JwtPropertiesTest` | JWT 설정 검증(빈 audience, 짧은 비밀키, JWKS와 비밀키 혼용 금지). |
| `common/config/runtime/RuntimeProfileGuardTest` | 프로필 조합 허용·거부(mybatis·persistence 동시 금지 등). |
| `common/config/runtime/LearningProfileGuardTest` | 운영에서 학습 문서 공개 금지. |
| `common/exception/GlobalExceptionHandlerTest` | 예상치 못한 오류의 안전한 응답, 405와 `Allow` 헤더 보존. |
| `common/security/SecurityHttpIntegrationTest` | 401·403, scope, audience·issuer·서명·만료, DTO 오류, 오류 dispatch, 미등록 경로 기본 거부, 비활성 토큰 거부, Redis 장애 시 503. |
| `common/security/OidcHttpIntegrationTest` | `oidc` 프로필의 JWKS 검증. |
| `common/security/PublicPrometheusHttpIntegrationTest` | Prometheus 공개 설정. |
| `common/security/SecurityProblemWriterTest` | 401 JSON과 Bearer challenge. |
| `hello/service/HelloServiceTest` | 주입된 시계 사용, HTTP 의존 없음. |
| `order/model/OrderQuoteTest` | 금액 계산 정확성, 수량 범위. |
| `order/service/impl/OrderServiceImplTest` | 참조 오류 422, 서버 가격 견적, 생성 시각, 소유자 검증, 수정 시 재견적 안 함, 삭제·조회의 404. |
| `order/web/OrderHttpIntegrationTest` | 메모리 모드 HTTP 계약(견적·생성·조회·수정·삭제, scope, 회원 관리자 권한, Swagger 제공, 다른 소유자 주문 숨김). |
| `order/messaging/OrderKafkaIntegrationTest` | `@Tag("kafka")`. 실제 브로커의 주문 변경 이벤트 발행·소비, Spring 트랜잭션 commit/rollback, 처리 재시도, 잘못된 JSON·반복 실패 DLT 및 후속 메시지 처리. |
| `order/repository/OrderPersistenceContractTest` | `@Tag("database")` 추상 기반 클래스. 일회용 PostgreSQL·Redis 컨테이너를 띄우고 JPA·MyBatis가 같이 지켜야 할 저장 계약 테스트를 정의한다. 아래 두 클래스가 상속해 각 구현으로 실행한다. |
| `order/repository/jpa/OrderPersistenceIntegrationTest` | `prod` 프로필 JPA 저장·소유권·rollback. |
| `order/repository/mybatis/MyBatisOrderPersistenceIntegrationTest` | `prod` + `mybatis`. MyBatis 구현만 선택되는지, JOIN·필터·페이징 안정성, 검색 HTTP 검증, SQL 와일드카드 문자 처리, 같은 snapshot의 count·목록. |
| `resources/sql/catalog.sql` | DB 테스트용 회원·상품 데이터. |

## 10. 주석 용어 정리 완료

2026-10-02에 아래 17개 소스 파일의 주석을 현재 `web/service/repository/model/error` 구조에 맞게 정리했다. 실행 코드와 프로필 조건은 변경하지 않았다.

| 파일 (`backend/src/main/java/com/example/backend` 기준) | 현재 설명 |
|---|---|
| `common/config/SecurityConfig.java` | 기능의 `web` 패키지에서 FeatureRoutes Bean으로 경로를 등록하고 Controller 메서드에 권한을 지정한다. |
| `member/repository/jpa/MemberJpaRepository.java`, `product/repository/jpa/ProductJpaRepository.java` | Spring Data 인터페이스와 Service가 사용하는 `repository`의 저장소 인터페이스를 구분한다. |
| `member/repository/jpa/MemberEntity.java`, `product/repository/jpa/ProductEntity.java` | JPA Entity를 `model`의 업무 객체로 변환한다. |
| `order/repository/OrderRepository.java` | Service가 사용하는 주문 저장소 인터페이스이며 JPA Entity·Spring Data 타입을 반환하지 않는다. |
| `member/error/MemberErrorCode.java`, `product/error/ProductErrorCode.java`, `order/error/OrderErrorCode.java` | 업무 오류는 기능별 `error` 패키지에서 정의하고 `model`·공개 Service 인터페이스에 HTTP 타입을 노출하지 않는다. |
| `member/web/MemberController.java` | 관리자 전용 DTO와 `service`의 업무 처리를 분리한다. |
| `order/repository/jpa/OrderRepositoryJpaImpl.java` | UUID는 OrderServiceImpl에서 생성하고 최종 commit은 서비스가 담당한다. |
| `member/web/MemberDtoConverter.java`, `product/web/ProductDtoConverter.java` | 업무 `model`을 HTTP 응답 DTO로 변환한다. |
| `product/service/ProductSnapshot.java` | 다른 기능에 필요한 불변 값만 공개하며 내부 모델·JPA Entity·HTTP DTO에 의존하지 않는다. |
| `member/service/impl/MemberServiceImpl.java` | 다른 기능에는 존재 검증만 공개하고 내부 `model`을 전달하지 않는다. |
| `common/security/permission/RequireRead.java`, `common/security/permission/RequireWrite.java` | `model`·Repository 인터페이스에 보안 프레임워크를 넣지 않는다. |

`application.yml`, 시계 Bean의 `applicationClock`, Spring Data의 `org.springframework.data.domain.Sort`, JWT 권한 `api.read/api.write` 등은 실제 설정·라이브러리·권한 이름이므로 유지했다. 주석·문서 변경은 diff와 정적 확인으로 검증했으며 빌드·컴파일·테스트는 실행하지 않았다.
