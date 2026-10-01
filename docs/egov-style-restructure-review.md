# 국내 개발자에게 익숙한 Spring Boot 구조로 전환하는 방향

검토·갱신일: 2026-10-01
대상: `C:\dev\personal\infra\spring-gateway-docker-sample` — Backend 중심, Gateway auth 일부
상태: **2026-10-01 소스에 전환을 적용했다. 1~10절은 전환 전 검토·설계 기록이며, 현재 구현은 [패키지 구조](package-structure.md)와 아래 11절을 따른다.**

## 1. 결론과 전환 목적

전자정부·국내 SI 방식에 익숙한 개발자가 업무 흐름을 쉽게 따라갈 수 있도록, 업무별 패키지는 유지하면서 `web → service → repository` 구조로 단순화한다. 이 프로젝트의 중심 목표인 무료 로컬 Kubernetes 학습과 재사용 가능한 Spring Boot 레퍼런스에 집중할 수 있도록 불필요한 연결 계층을 줄인다.

Backend의 업무 서비스는 `Service` 인터페이스와 `service.impl.ServiceImpl`을 기본 형태로 정한다. 이는 개발자에게 익숙한 일관성을 위한 선택이며, Spring이나 JPA가 기술적으로 요구하는 조건은 아니다. 단순한 공통 유틸·값 객체까지 인터페이스/Impl로 나누지는 않는다.

전자정부의 모든 관례를 그대로 복제하지는 않는다. DAO·Entity·Spring Data Repository를 `service.impl`에 몰아넣지 않고 별도 `repository`에 둔다. 기존 JPA, 요청/응답 DTO, 불변 업무 객체, 보안과 트랜잭션은 유지한다. 전자정부 프레임워크 라이브러리와 MyBatis는 이번 전환에 도입하지 않는다.

**단순 파일 이동과 실제 의존 관계 변경을 구분한다.** 패키지 이동은 비교적 기계적으로 처리할 수 있지만, 주문 서비스 통합·기능 간 연결 계층 제거·ArchUnit 재설계는 동작 보존을 확인해야 하는 리팩터링이다. 기능 보존은 목표이며, 전환 후 검증 전에 완료됐다고 단정하지 않는다.

## 2. 목표 패키지 구조

최상위 `com.example`는 유지한다. 회사·서비스 도메인으로 바꾸는 작업은 별도 변경으로 분리한다.

```text
com.example.backend
├── common                         기존 공통 설정·보안·예외·HTTP 처리 유지
├── hello
│   ├── web                        HelloController, HelloRoutes, dto/
│   └── service
│       ├── HelloService
│       └── impl/HelloServiceImpl
├── member
│   ├── web                        MemberController, MemberRoutes,
│   │                              MemberDtoConverter, RequireMemberAdmin, dto/
│   ├── service
│   │   ├── MemberService
│   │   └── impl/MemberServiceImpl
│   ├── repository
│   │   ├── MemberRepository       fixture/JPA가 구현하는 저장소 인터페이스
│   │   ├── jpa                    MemberRepositoryJpaImpl, MemberJpaRepository,
│   │   │                          MemberEntity
│   │   └── fixture                FixtureMemberRepository
│   ├── model                      Member
│   └── error                      MemberErrorCode
├── product                        member와 같은 배치
│   ├── service                    ProductService, ProductSnapshot, impl/
│   └── model                      Product (기능 내부 업무 객체)
└── order
    ├── web                        OrderController, OrderRoutes,
    │                              OrderDtoConverter, dto/
    ├── service
    │   ├── OrderService           preview / place / get
    │   └── impl/OrderServiceImpl
    ├── repository
    │   ├── OrderRepository
    │   ├── jpa                    OrderRepositoryJpaImpl, OrderJpaRepository,
    │   │                          OrderEntity
    │   └── fixture                FixtureOrderRepository
    ├── model                      OrderQuote, StoredOrder
    └── error                      OrderErrorCode

libs/platform-core                기존 공통 응답·오류·인증 계약 유지
```

`model`은 기존 `domain`의 업무 객체를 옮기는 위치다. HTTP 요청/응답 DTO나 JPA Entity를 함께 넣지 않는다. `OrderQuote`의 수량·가격 검증과 금액 계산, `StoredOrder`의 소유자·주문 시각 등 기존 의미를 유지한다. `record`를 일괄적으로 가변 VO로 바꾸지 않는다.

`Repository`는 JPA와 현재 코드의 용어를 유지하기 위한 기본 명칭이다. 팀 표준상 DAO가 반드시 필요하면 별도로 명명 규칙을 정할 수 있지만, 이번 기본안에서는 `*DAO`로 일괄 변경하지 않는다. 저장소 교체를 위한 인터페이스는 계속 필요하다.

저장소 구현 명칭은 다음과 같이 확정한다. product·order에도 같은 규칙을 적용한다.

| 역할 | 회원 기능의 이름 |
|---|---|
| 업무 서비스가 사용하는 저장소 인터페이스 | `MemberRepository` |
| 직접 작성한 JPA 저장소 구현 | `MemberRepositoryJpaImpl` |
| Spring Data가 구현하는 인터페이스 | `MemberJpaRepository` |
| 기존 fixture 구현 | `FixtureMemberRepository` |

`MemberRepositoryJpaImpl`은 `MemberRepository`를 구현하고 `MemberJpaRepository`를 주입받는 일반 저장소 Bean이다. `MemberJpaRepositoryImpl`이라는 이름은 사용하지 않는다. 기본 postfix가 `Impl`인 Spring Data는 스캔 범위의 저장소 인터페이스명 + `Impl`을 구형 커스텀 구현 탐색 규칙으로 인식할 수 있다. 의도하지 않은 자동 연결을 피하려는 명명 기준이며, 모든 `Impl` 접미사를 금지하는 뜻은 아니다. postfix·탐색 설정이 바뀌면 그 설정도 확인한다. [Spring Data 공식 문서](https://docs.spring.io/spring-data/jpa/reference/repositories/custom-implementations.html)

기능별 `error`에는 `MemberErrorCode`, `ProductErrorCode`, `OrderErrorCode`처럼 업무 오류를 정의하는 enum을 둔다. 기존 `common/exception`과 `platform-core/exception`의 실제 예외 클래스·처리기 패키지는 그대로 유지한다.

다른 기능에 공개하는 `ProductSnapshot`은 `product.service` 바로 아래에 둔다. `product.model.Product`는 기능 내부 업무 객체로 유지한다. 공개 snapshot은 내부 model·Entity·웹 DTO를 필드 타입으로 노출하지 않는다. Repository는 내부 model을 반환하고 `ProductServiceImpl`이 공개 snapshot으로 변환한다. 이로써 Repository가 service의 공개 값 타입을 참조하는 역방향 의존도 피한다.

## 3. 전환 전 구조와 변경 대응

| 현재 | 변경 방향 | 보존·확인할 사항 |
|---|---|---|
| `feature/api` | `feature/web` | URL, DTO, 경로 등록, 권한 애너테이션 유지 |
| `application/*Service` | `service/*Service` + `service/impl/*ServiceImpl` | 외부 호출 계약과 주입 관계 확인 |
| 주문의 `OrderQuoteService`, `PlaceOrderService`, `OrderQueryService` | 후속 단계에서 `OrderServiceImpl`로 통합 | 생성·조회 트랜잭션과 소유권 검사 유지 |
| `feature/domain` | `feature/model` | 불변식·계산·불변 객체 유지 |
| `application/port/*Repository` | `repository/*Repository` | 저장소 인터페이스와 반환 의미 유지 |
| `infrastructure/persistence` | `repository/jpa` | Entity·Spring Data 인터페이스·프로필 유지, 직접 작성한 구현은 `*RepositoryJpaImpl` |
| `infrastructure/fixture` | `repository/fixture` | JPA와 상호 배타적인 선택 조건 유지 |
| `MemberLookup`, `ProductLookup` | 기능별 Service 인터페이스에 필요한 조회 메서드 통합 | `exists`, `findProduct`와 실패 의미 유지 |
| `product/contract/ProductSnapshot` | `product/service/ProductSnapshot` | 다른 기능에 공개하는 불변 조회 값, 내부 model·Entity 참조 금지 |
| `OrderCatalog`, `LocalOrderCatalog` | 주문 통합 단계에서 제거 | 다른 기능의 Service 인터페이스를 직접 사용 |
| `application/error/*ErrorCode` | `error/*ErrorCode` | 업무 오류 enum의 코드·HTTP 상태·응답 의미 유지 |
| MapStruct `*Mapper` | `web/*DtoConverter` | MapStruct 생성 방식과 매핑 누락 검사 유지 |
| `common`, `libs/platform-core` | 유지 | 기능 전용 코드를 공통 모듈로 이동하지 않음 |

목표 의존 방향은 `web → service 인터페이스 → service.impl → 자기 repository`다. 다른 기능을 호출할 때는 그 기능의 `service` 직속 패키지에 둔 Service 인터페이스와 공개 값 타입만 사용한다. 다른 기능의 `service.impl`·repository·model·error·web은 직접 참조하지 않는다. 자기 기능의 model·error 사용과 공통 모듈 참조는 별도로 허용한다. 기능 간 순환 참조는 계속 금지한다.

## 4. 기능 간 호출을 단순화할 때의 기준

전환 전 주문은 `OrderCatalog → LocalOrderCatalog → MemberLookup/ProductLookup`을 거친다. 전환 후에는 `OrderServiceImpl`이 `MemberService`, `ProductService` 인터페이스를 주입받도록 줄일 수 있다.

다만 조회 메서드와 오류 의미를 먼저 확정해야 한다.

- 회원 확인은 `MemberService.exists(memberId)`처럼 존재 여부를 반환한다.
- 상품 조회는 `ProductService.findProduct(productId)`의 `Optional<ProductSnapshot>`을 유지하며, `ProductSnapshot`은 `product.service`에 둔다. 다른 기능에는 내부 `Product`나 Entity·웹 DTO를 반환하는 메서드를 사용하지 않는다. 같은 기능의 웹 계층이 사용하는 기존 list/get 메서드의 model 반환을 다른 기능에도 허용한다는 뜻은 아니다.
- 주문은 회원·상품이 없으면 기존 `422 / INVALID_MEMBER_REFERENCE`, `422 / INVALID_PRODUCT_REFERENCE`를 생성한다.
- 단순히 `MemberService.get()` 또는 `ProductService.get()`으로 치환하면 해당 기능의 `404 / MEMBER_NOT_FOUND`, `404 / PRODUCT_NOT_FOUND`로 달라질 수 있으므로 피한다.
- 주문 조회의 `findByIdAndOwnerSubject` 조건과 다른 사용자 주문에 대한 `404 / ORDER_NOT_FOUND` 처리를 유지한다.

중복된 snapshot 변환이 사라질 수는 있지만, 어떤 데이터를 다른 기능에 공개하는지까지 무제한으로 넓히지는 않는다. 연결 계층을 줄인 결과 기능 간 결합이 늘어나는 점은 수용하되, 공개 Service 계약과 순환 금지로 관리한다.

## 5. 주문 서비스와 트랜잭션

전환 전 주문 생성은 `PlaceOrderService.place()`의 `@Transactional`로 회원·상품 확인부터 저장까지 묶는다. 조회는 `OrderQueryService.get()`의 `@Transactional(readOnly = true)`로 처리한다.

통합한 `OrderServiceImpl`에서도 외부에서 호출되는 public `place()`와 `get()`에 각각 이 경계를 유지한다. 견적 `preview()`는 저장하지 않는 동작으로 유지한다. 생성 메서드가 내부 견적 계산 메서드를 호출하는 것은 가능하지만, 내부 호출에 붙인 애너테이션으로 별도의 트랜잭션이 시작된다고 가정하지 않는다. Spring 기본 프록시 방식에서는 자기 내부 호출에 트랜잭션 advice가 새로 적용되지 않는다. [Spring 트랜잭션 공식 문서](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/annotations.html)

기존 `OrderPersistenceIntegrationTest.transactionRollsBackEvenAfterSqlWasFlushed()`는 바깥에서 `TransactionTemplate`으로 트랜잭션을 시작한다. 이 테스트는 유지할 가치가 있지만, 통과하더라도 서비스의 `@Transactional`이 누락되지 않았다는 증거로 충분하지 않다.

전환 검증에는 호출자가 트랜잭션을 열지 않은 상태에서 Spring Bean의 주문 생성 메서드를 호출하고, 저장 이후 실패 시 전체 rollback을 확인하는 검증을 포함한다. 이 검증을 위한 별도 실패 조건은 테스트 범위에서 구성하고 운영 API를 추가하지 않는다.

## 6. ArchUnit은 새 구조에 맞춰 재설계

기존안의 “규칙 3개를 패키지 이름만 치환한다”는 설명은 적용하지 않는다. 검토 당시 Backend에는 3개, Gateway에는 4개의 구조 검사가 있으며 검사 대상과 의도가 서로 다르다.

`..application..`을 `..service..`로, 금지 대상 `..infrastructure..`를 `..service.impl..`로 단순 치환하면 `service.impl` 자신도 검사 대상에 포함된다. 정상적인 구현체 내부 의존까지 차단할 수 있으므로 인터페이스·구현체·저장소의 책임에 맞게 조건을 다시 작성한다.

유지할 핵심 기준:

- Service와 Repository는 웹 Controller·요청/응답 DTO에 의존하지 않는다.
- Repository 인터페이스와 JPA/fixture 구현 전체는 모든 기능의 service 및 service 하위 패키지를 참조하지 않는다. 공개 snapshot 변환은 ServiceImpl에서 수행한다.
- Service 인터페이스는 구현체·JPA Entity·Spring Data Repository 타입을 노출하지 않는다.
- 기능 간 참조는 상대 기능의 service 직속 패키지만 허용하고, 공개 값 타입도 그곳에 둔다. service.impl을 비롯한 하위 패키지는 허용하지 않는다. 기능 간 순환은 별도로 금지한다.
- common과 platform-core는 업무 기능에 의존하지 않는다.
- 기능의 HTTP handler에는 기존 메서드 권한 선언이 있다.

패키지 허용 조건은 `com.example.backend.product.service`처럼 정확한 상대 기능의 service 패키지를 대상으로 한다. `..service..`는 service.impl까지 포함하므로 외부 공개 범위로 사용하지 않는다. 필요하면 패키지 경로에서 기능 이름을 추출해 자기 기능과 타 기능을 구별한다. 따라서 `ProductSnapshot`만 클래스 이름으로 예외 허용하는 목록은 필요하지 않다. 공개 snapshot 자체가 내부 model·Entity를 노출하지 않는지도 확인한다.

HTTP 권한 검사는 현재 `.api` 패키지를 선택하는 코드가 있으므로 `.web` 전환에 맞춰 수정한다. 검사 대상 Controller/handler가 0개여서 통과하는 일이 없도록 대상 존재도 확인한다. 전환 완료 검증에서는 `-PskipArchitecture`를 사용하지 않는다.

## 7. 유지할 기능과 이번 범위에서 제외할 변경

다음 항목은 변경 대상이 아니며, 이동 후에도 보존됐는지 확인한다.

- API URL, 성공 응답 envelope, Problem Details, 상태 코드와 요청 ID 처리.
- `FeatureRoutes`, `@RequireRead`, `@RequireWrite`, `@RequireMemberAdmin`, Gateway scope 정책.
- local/test fixture와 persistence 또는 dev/staging/prod JPA의 저장소 선택 조건.
- DTO 검증, 서버 가격 계산, 주문 소유권, DB 제약 조건, Flyway migration.
- Keycloak/OIDC·데모 인증 분리와 기존 로그아웃·토큰 처리 의미.
- Docker/Kubernetes 배포 구성, 기존 DB와 볼륨.

컴포넌트 스캔 루트 안에 파일을 두는 것만으로 모든 동작 보존이 증명되지는 않는다. Bean 등록·프로필·주입 대상, JPA 스캔, Advice의 대상 범위, MapStruct 생성 코드와 테스트 패키지 참조까지 확인한다.

최상위 패키지는 유지한다. 프로필 YAML에는 실제로 `com.example.backend`, `com.example.gateway` 로깅 설정이 있으므로, 나중에 최상위 이름을 바꾸면 이 설정과 스캔·테스트 대상도 함께 변경해야 한다.

MyBatis로의 전환, 전자정부 프레임워크 라이브러리 도입, Spring/Java 버전 변경은 별도 작업으로 둔다. 이번 변경을 전자정부 프레임워크 자체 도입이나 적용 적합성 확보로 표현하지 않는다.

## 8. Gateway 적용 범위

Gateway는 `auth/api → auth/web`, `auth/application → auth/service` 중심으로 이름을 정리한다. Backend와 동일한 구조를 맞추기 위해 불필요한 DAO·Entity 계층을 만들지는 않는다.

`AuthController`와 `AuthExceptionHandler`는 **함께 `auth.web`으로 이동**한다. `@RestControllerAdvice(basePackageClasses = AuthController.class)`의 클래스 참조, `@ConditionalOnDemoIssuer`, `@Order`와 오류 응답 의미를 유지한다. 현재 `AuthErrorCode`는 실제 enum이므로 `auth.error`로 옮기고 Handler의 참조도 갱신한다.

다만 “두 클래스가 같은 패키지에 있어야 Advice가 동작한다”는 설명은 정확하지 않다. `basePackageClasses`는 지정한 `AuthController`가 속한 패키지와 하위 패키지의 Controller를 적용 대상으로 선택하며, Advice 클래스 자신의 위치를 제한하지 않는다. 따라서 함께 이동하는 것은 인증 웹 처리 코드를 모으기 위한 배치 원칙이다. Advice가 Spring Bean으로 등록되고 해당 프로필에서 활성화되는지도 확인해야 한다. 이 선택자는 AuthController 한 클래스만 대상으로 제한하는 것도 아니다. [RestControllerAdvice 공식 API](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/web/bind/annotation/RestControllerAdvice.html)

`TokenService`, `LoginService`의 단순 명칭·배치 변경과 세션 저장소 연결 책임을 구분한다. `LoginSessions` 같은 실제 교체 경계는 유지하고, Redis 연결 구현은 service 업무 코드와 구별되는 위치에 둔다. 상세 배치는 Backend pilot 결과를 보고 정한다.

**WebFlux의 `Mono` 흐름과 비동기 Redis 처리는 유지한다.** 동기식 Backend 서비스처럼 보이게 하려고 `.block()`을 넣거나 MVC 방식으로 전환하지 않는다. 인증 기능 Advice·프로필 조건·토큰 관련 회귀 검증도 유지한다.

## 9. 단계별 전환과 완료 기준

1. **현재 상태 기록:** 작업트리의 기존 변경과 실행 중인 Compose 구성을 확인한다. 현재 구조·API·보안 동작을 기준으로 삼고 다른 세션의 변경을 덮어쓰지 않는다.
2. **product pilot:** web/service/repository/model/error 배치와 Service/Impl, DtoConverter 명칭을 적용한다. 기존 공개 조회 계약은 우선 유지하고, 해당 이동과 관련된 구조 검사도 함께 갱신한다.
3. **member·hello 적용:** pilot에서 확정한 규칙을 적용한다. 이름·위치 변경과 동작 변경이 섞이지 않게 구분한다.
4. **order 위치 정리:** 기존 견적·생성·조회 분리는 일단 유지한 채 패키지를 옮긴다. 기존 API·오류·권한·저장소 선택이 유지되는지 확인한다.
5. **order 단순화:** `OrderService/OrderServiceImpl`로 통합하고, 필요한 조회 메서드를 Member/Product Service 인터페이스로 옮긴 뒤 중복 contract/port/adapter를 제거한다. 오류 계약과 트랜잭션 검증을 수행한다.
6. **Gateway 정리 및 전체 검증:** 필요한 auth 패키지만 정리하고 공통 모듈·Backend·Gateway 테스트와 DB 통합 테스트를 실행한다. `databaseTest`는 일회용 테스트 컨테이너를 사용하며 실행 중인 학습 DB를 테스트 대상으로 바꾸지 않는다.
7. **문서 정합성:** 최신 구조 문서와 학습 가이드를 실제 코드에 맞춘다. 06·11·12·13·14 외에도 15·16·17 및 README 등 관련 참조를 검색한다. 현재 따라 할 명령·경로는 갱신하고, 과거 구조나 테스트 수를 남기면 당시 기록임을 표시한다.

완료 검증 항목:

- 컴파일 및 MapStruct 생성 성공, Bean 주입 충돌 없음. `*RepositoryJpaImpl`이 의도한 저장소 Bean으로 등록되고 Spring Data 커스텀 구현으로 오인 연결되지 않음.
- 기능별 회귀 테스트와 새 ArchUnit 규칙 통과, 권한 검사 대상이 비어 있지 않음. service 직속 공개 범위와 repository → service 역방향 의존 금지를 확인함.
- 기존 200/201/400/401/403/404/422/429 계약 중 해당 기능의 기대 동작 유지.
- fixture 및 JPA 환경의 API 등록·저장소 선택 확인.
- 본인 주문 조회, 타인 주문 차단, 서버 가격 계산, DB 저장·rollback 확인.
- Gateway 비동기 인증·로그아웃 경로 및 기존 OIDC 구성이 유지됨. 이동한 AuthExceptionHandler가 데모 인증의 기존 401/503 오류를 처리하고, 적용 범위·프로필 조건이 유지됨.
- 필요한 최종 런타임 확인은 현재 Compose 파일 조합을 유지하고 프로젝트 범위에서 수행함. 단순 상태 확인을 위해 정상 실행 중인 서비스를 불필요하게 재생성하지 않음.

위 항목은 설계 당시 정한 구현 완료 기준이다. 실제 적용과 실행 결과는 11절에 별도로 기록한다.

## 10. 검토 근거

전환 전 소스에서 다음 파일을 대조했다. 아래 경로는 검토 당시 위치이며 현재 경로는 package-structure.md를 따른다.

- `docs/package-structure.md`: 현재 기능·공통 모듈 경계와 검증 범위.
- `backend/src/main/java/com/example/backend/order/application/OrderQuoteService.java`: 주문 참조 오류 처리.
- `backend/src/main/java/com/example/backend/order/application/command/PlaceOrderService.java`, `query/OrderQueryService.java`: 생성·조회 트랜잭션과 소유권.
- `backend/src/main/java/com/example/backend/member/application/MemberService.java`, `product/application/ProductService.java`: 공개 조회와 단건 조회의 오류 의미 차이.
- `backend/src/test/java/com/example/backend/architecture/ArchitectureTest.java`, `gateway/src/test/java/com/example/gateway/architecture/ArchitectureTest.java`: 패키지 선택과 구조·권한 규칙.
- `backend/src/test/java/com/example/backend/order/api/OrderHttpIntegrationTest.java`: 주문의 422 계약.
- `backend/src/test/java/com/example/backend/order/infrastructure/persistence/OrderPersistenceIntegrationTest.java`: 저장·소유권·rollback 검증 방식.
- Gateway auth 서비스·Advice, 프로필별 `application-*.yml`: 비동기 처리와 패키지 관련 설정.

[전자정부 CRUD 생성 가이드](https://www.egovframe.go.kr/docs/5.0/egovframe-development/implementation-tool/code-generation/template-based-code-generation/eclipse-crud-code-generation/)는 Service/ServiceImpl·DAO·VO·Controller 구성의 참고 근거다. 이 문서의 repository 분리와 단계별 전환 범위는 해당 가이드를 현재 프로젝트 목적에 맞게 조정한 설계안이다.

## 11. 적용 기록 — 2026-10-01

- Backend hello/member/product/order를 web, service 인터페이스·impl, repository, model, error로 이동했다. 실제 코드가 있는 계층만 둔다.
- Member/Product/Order의 직접 작성한 JPA 구현은 `*RepositoryJpaImpl`, Spring Data 인터페이스는 `*JpaRepository`로 구별한다. MapStruct는 `*DtoConverter`로 정리했다.
- OrderServiceImpl에 preview/place/get을 통합하고 MemberService.exists·ProductService.findProduct를 사용한다. ProductSnapshot은 product.service에 두고 중복 contract/port/adapter를 제거했다.
- 생성의 Transactional, 조회의 readOnly·소유자 조건, 서버 가격 계산·422/404 의미를 유지한다. 기존 회귀 사례를 통합 서비스 테스트로 옮겼다.
- Gateway AuthController/Advice를 auth.web으로 함께 이동하고 auth.service·repository/redis·error로 정리했다. 데모/OIDC 조건과 Mono 흐름은 유지한다.
- ArchUnit을 공개 service 직속 경계, 저장소의 역방향 의존 금지, 공통 모듈과 기능 분리, 순환·권한 검사에 맞춰 갱신했다. 검사 대상이 비어 있는 권한 검증도 실패한다.
- 현재 소스 경로와 호출 흐름을 README·패키지/영속성/API 계약·학습 문서에 반영했다. 과거 날짜의 검증 기록은 당시 결과로 보존한다.

검증 결과:

- platform-core 4개, Backend 일반 61개, Gateway 63개, PostgreSQL/Redis DB 통합 8개: **136개 통과, 실패·건너뜀 0**. ArchUnit을 제외하지 않았다.
- 서비스 경계의 실제 INSERT 후 rollback, readOnly 조회, 422 참조 오류·주문 소유권과 기존 인증 회귀 검증을 통과했다. Gateway 로그인 저장소 실패의 503 Advice 응답도 확인했다.
- `:backend:bootJar :gateway:bootJar` 성공. 두 JAR에서 새 패키지를 확인하고 이전 기능 패키지 클래스가 남지 않았음을 검사했다.
- `scripts/verify.sh`의 Bash·Kustomize·Compose·JSON 정적 검사, Java 130개 파일의 package/경로 일치 및 수정 Markdown의 로컬 링크 121개 검사를 통과했다.
- 첫 DB 실행에서 테스트 예상 예외가 Spring Repository의 예외 변환과 달라 실패했다. 예상 타입과 원인 검사를 바로잡은 뒤 DB 8개를 재검증했다. 운영 코드를 바꿔 테스트를 우회하지 않았다.
- 커밋 전 재검토에서도 일반 테스트 128개와 DB 통합 8개, 양쪽 JAR 및 정적 검사를 다시 통과했다. 재검증 중 Docker 엔진 종료로 DB 초기화가 실패한 실행은 성공으로 집계하지 않았으며, 엔진 기동 후 DB 테스트를 별도로 완료했다.

Docker Desktop 엔진은 DB 테스트를 위해 기동했다. 기존 Compose 컨테이너는 중지 상태를 유지했고 DB 볼륨은 변경하지 않았다. Compose 이미지 재배포·실제 Keycloak 브라우저 로그인·Kubernetes/AWS 운영 검증은 수행하지 않았다. OIDC와 인증 모드 분리는 자동 테스트로 확인한 범위다.
