# 기능별 web / service / repository 구조

2026-10-01에 [구조 전환 설계](egov-style-restructure-review.md)를 적용했다. 국내 Service/ServiceImpl 관례를 사용하되 JPA, 불변 업무 모델, HTTP DTO와 공통 플랫폼 계약을 유지한다. 전자정부 프레임워크 라이브러리는 도입하지 않았다. 이후 선택형 [MyBatis 예제](learning/18-mybatis-and-sql-queries.md)를 추가했다.

## 현재 배치

```text
backend / com.example.backend
├── common                      앱별 설정·보안·MVC 오류 처리·요청 컨텍스트
├── hello
│   ├── web                     HelloController, HelloRoutes, dto/
│   └── service                 HelloService, impl/HelloServiceImpl
├── member                      product도 동일한 기본 배치
│   ├── web                     MemberController, MemberRoutes, MemberDtoConverter,
│   │                           RequireMemberAdmin, dto/
│   ├── service                 MemberService, impl/MemberServiceImpl
│   ├── repository              MemberRepository
│   │   ├── jpa                 MemberRepositoryJpaImpl, MemberJpaRepository, MemberEntity
│   │   ├── mybatis             MemberRepositoryMyBatisImpl, MemberSqlMapper, MemberMyBatisConfig
│   │   └── MemoryMemberRepository
│   ├── model                   Member
│   └── error                   MemberErrorCode
├── product
│   └── service                 ProductService, ProductSnapshot, impl/ProductServiceImpl
└── order
    ├── web                     OrderController, OrderRoutes, OrderDtoConverter, dto/
    ├── service                 OrderService, OrderSearchService, impl/
    ├── repository              OrderRepository, MemoryOrderRepository, jpa/, mybatis/
    ├── model                   OrderQuote, StoredOrder, OrderSearchCriteria, OrderSummary, OrderSearchResult
    └── error                   OrderErrorCode

gateway / com.example.gateway
├── common                      WebFlux 설정·보안·오류 처리·JWT decoder·Redis I/O
└── auth
    ├── web                     AuthController, AuthExceptionHandler, dto/
    ├── service                 LoginService, TokenService (Mono 흐름 유지)
    ├── repository              LoginSessions, redis/RedisLoginSessions
    └── error                   AuthErrorCode, InvalidCredentialsException

libs/platform-core / com.example.platform
├── code                        ErrorCode, CommonErrorCode, SuccessCode
├── response                    ApiResponse, ApiResponses
├── exception                   BusinessException, ProblemDetails
├── security                    JwtAuthorities, token/TokenKey
└── util                        ErrorDiagnostics
```

## 역할과 명명

- Backend 업무 서비스는 `service/*Service` 인터페이스와 `service/impl/*ServiceImpl`로 나눈다. Controller는 인터페이스를 주입받는다.
- HTTP DTO와 MapStruct `*DtoConverter`는 기능의 `web`에 둔다. 생성 구현은 build/generated에 있으며 편집하거나 Git에 넣지 않는다.
- `model`은 불변 업무 객체와 금액 계산 등 규칙을 담는다. JPA Entity·HTTP DTO와 구별한다.
- 저장소 인터페이스는 `repository`, 직접 작성한 JPA 구현은 `repository/jpa/*RepositoryJpaImpl`, Spring Data 인터페이스는 `*JpaRepository`이다. `*JpaRepositoryImpl`은 Spring Data 커스텀 구현 탐색과 혼동되므로 사용하지 않는다.
- 메모리 구현체는 `repository/Memory*Repository`에 직접 둔다. `fixture`나 `memory` 하위 패키지는 만들지 않는다. 회원·상품은 고정 샘플 목록을 조회하고, 주문은 메모리에 저장한다.
- 기능 `error`의 오류 enum은 플랫폼 `ErrorCode`를 구현한다. `BusinessException`과 앱별 처리기가 Problem Details로 변환한다. 기존 공통 `exception` 패키지는 유지한다.
- MyBatis 구현은 `repository/mybatis/*RepositoryMyBatisImpl`, SQL 인터페이스는 `*SqlMapper`, XML은 `resources/mapper/<feature>`에 둔다. SQL Mapper와 MapStruct DTO Converter를 구분한다.
- Gateway는 작은 인증 서비스의 클래스 배치를 정리했다. 형식만 맞추기 위한 Service/Impl·DAO·Entity는 추가하지 않는다. 실제 Redis 교체 경계인 `LoginSessions`는 유지한다.

## 의존 방향과 공개 범위

`web → service 인터페이스 → service.impl → 자기 repository`를 기본으로 한다. 업무 모델과 오류는 자기 기능에서 사용한다.

- Service와 Repository는 web을 참조하지 않는다. Repository 인터페이스·메모리·JPA 구현은 모든 기능의 service를 참조하지 않는다.
- 다른 기능에서 사용할 수 있는 패키지는 **상대 기능의 service 직속 패키지**다. `service.impl`, repository, model, error, web은 내부이다. ArchUnit에서 `..service..` 전체를 공개하지 않는다.
- `MemberService.exists`는 boolean, `ProductService.findProduct`는 `Optional<ProductSnapshot>`을 반환한다. `ProductSnapshot`은 `product.service`의 공개 record이며 내부 model·Entity를 노출하지 않는다. ProductServiceImpl이 내부 Product를 snapshot으로 변환한다.
- 같은 기능의 web에서 사용하는 `list/get`의 model 반환을 다른 기능에서 사용하지 않는다. 주문은 부재를 자신의 422 오류로 처리하고 상품/회원 단건 조회의 404를 가져오지 않는다.
- 기능 간 순환과 common/platform-core → 기능 의존은 금지한다. 공통에는 업무 URL·권한·오류 enum을 넣지 않는다.
- 공통 계약만 플랫폼 모듈에서 공유한다. Gateway WebFlux와 Backend MVC 처리기·Redis I/O 구현은 각각 유지한다.

## 주문과 트랜잭션

OrderServiceImpl이 MemberService와 ProductService를 직접 주입받는다. 중복 조회 계약/adapter를 없애고 `preview`, `place`, `get`으로 통합했다.

- `preview`: 서버 가격으로 견적을 계산하고 저장하지 않는다.
- `place`: public 메서드의 `@Transactional` 안에서 회원·상품 검증부터 INSERT까지 처리한다. 소유자와 UTC Clock을 사용하며 자기 내부 preview 호출에 추가 트랜잭션을 기대하지 않는다.
- `get`: `@Transactional(readOnly = true)`를 유지하고 `findByIdAndOwnerSubject`로 소유자 범위를 제한한다. 타인/부재 주문은 404다.
- DB 테스트는 바깥 TransactionTemplate의 rollback 사례와, 호출자 트랜잭션 없이 서비스 Bean을 호출한 뒤 실제 INSERT/flush 후 실패하는 사례를 모두 검사한다.

## 프로필과 기능 등록

Backend Controller/Service는 항상 등록하고 저장소 구현만 프로필로 고른다.

| lifecycle | 추가 profile | 저장소 | API |
|---|---|---|---|
| local / test | 없음 | 메모리 저장소 | 조회·견적·생성·본인 조회 |
| local / test | persistence | PostgreSQL/JPA | 동일 |
| dev / staging / prod | 없음 | PostgreSQL/JPA | 동일 |
| 하나의 lifecycle | mybatis | PostgreSQL/MyBatis | 동일 + 본인 주문 조건 검색 |

JPA/MyBatis는 DB_URL·DB_USERNAME·DB_PASSWORD가 필수다. persistence와 mybatis는 동시에 선택하지 않는다. 메모리에 저장한 주문 데이터는 재시작 시 사라지며 수평 확장 내구성을 검증하지 못한다. Swagger/OpenAPI는 기존 local/test 공개 설정을 따른다.

Gateway의 AuthController와 AuthExceptionHandler는 auth.web에 함께 있다. Advice의 basePackageClasses는 AuthController 패키지와 하위 Controller를 선택한다. Advice 자체의 같은 패키지 배치는 프레임워크 필수 조건이 아니다. 데모 발급 조건과 OIDC 분리, Mono와 비동기 Redis 흐름은 유지한다.

## 새 기능 추가

1. 기능의 web에 Controller/DTO를, service에 인터페이스와 impl 구현을 둔다. 필요한 model·repository만 추가한다.
2. web의 Configuration에서 FeatureRoutes Bean으로 경로를 등록한다. 각 HTTP handler에 RequireRead/RequireWrite 또는 기능 소유 PreAuthorize를 선언한다.
3. 소유권·수량·업무 상태는 service/model에서 검증한다. Repository 조회도 소유자 범위를 제한한다.
4. 다른 기능에는 service의 최소 조회 값만 공개한다. 순환이 필요해지면 상위 업무 책임을 검토한다.
5. 기본 `/api/**` 외 영역을 공개하면 Gateway 경로와 상위 권한 정책도 검토한다.

JWT 인증 → MVC DTO 바인딩/검증 → 메서드 권한 → 업무 로직 순서다. 잘못된 DTO와 권한 부족이 함께 있으면 Backend에서 400이 먼저 나올 수 있다. RequireMemberAdmin은 member.web의 Controller 전용이다.

## 설정·빌드·검증

환경변수와 YAML은 Spring Environment를 거쳐 ConfigurationProperties에 바인딩된다. TimeConfig의 Clock은 HelloServiceImpl·OrderServiceImpl·TokenService에 주입한다. 응답 meta.timestamp는 응답 생성 시각이다.

루트 settings.gradle, Wrapper와 version catalog를 사용한다. Docker build context는 루트이고 앱별 Dockerfile은 플랫폼 계약과 대상 앱만 빌드한다.

```bash
bash ./gradlew :libs:platform-core:test :backend:test :gateway:test
bash ./gradlew :backend:databaseTest
bash ./gradlew :backend:bootJar :gateway:bootJar
bash scripts/verify.sh
```

Backend ArchUnit은 web 의존·저장소의 service 의존·공개 service 내부 구현 노출·기능 간 공개 패키지·common/platform 경계·순환·HTTP 메서드 권한을 검사한다. 권한 검사 대상이 비어도 실패한다. Gateway는 service/web/Redis 구현 경계·repository 역방향 의존·common/기능 경계·순환을 검사한다. 전환 검증에는 skipArchitecture를 사용하지 않는다.

HTTP 테스트는 메모리 저장소와 인증 정책을, databaseTest는 일회용 PostgreSQL/Redis에서 prod JPA와 prod,mybatis의 공통 저장·소유권·rollback 계약 및 MyBatis 검색을 검증한다. 실제 운영 배포나 AWS/EKS 검증을 의미하지 않는다. 기존 Compose 재배포 시 scripts/reference-stack.sh와 기존 파일/profile 조합을 사용한다.
