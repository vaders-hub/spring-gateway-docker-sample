# 18. PostgreSQL · MyBatis 저장소 교체와 조건 검색

12단계 JPA와 같은 PostgreSQL 스키마·Flyway migration·서비스·API를 사용하면서 저장소 구현을 MyBatis로 교체합니다. 추가 주문 검색 API는 조건별 SQL, JOIN, count와 페이징을 보여줍니다. Oracle과 JPA/MyBatis 혼합 쓰기 트랜잭션은 이 단계의 구현 범위가 아닙니다.

JPA도 JPQL·native SQL 등을 통해 복잡한 조회를 구현할 수 있습니다. 이 예제는 복잡한 SQL을 XML에서 직접 관리하는 방법을 비교하기 위한 선택지입니다. 국내 개발자 전체의 사용 비율이나 특정 기술의 우열을 전제하지 않습니다.

## 18-1. 구성과 읽는 순서

Java 25·Spring Boot 4.0.8을 유지하고 MyBatis Starter 4.0.0을 추가했습니다. 의존성 버전은 `gradle/libs.versions.toml`에서 관리합니다. [Starter 공식 호환표](https://mybatis.org/spring-boot-starter/mybatis-spring-boot-autoconfigure/)의 Boot 4 대응 계열을 사용합니다.

```text
<feature>/repository/
├── MemberRepository / ProductRepository / OrderRepository
├── Memory*Repository
├── jpa/                  *RepositoryJpaImpl, *JpaRepository, *Entity
└── mybatis/              *RepositoryMyBatisImpl, *Mapper

order/model/              OrderSearchCriteria, OrderSummary, OrderSearchResult
order/service/            OrderSearchService, impl/OrderSearchServiceImpl
order/web/                OrderSearchController, OrderDtoConverter, dto/
resources/mapper/         member/, product/, order/의 SQL XML
common/config/            MyBatisConfig
common/persistence/mybatis/UuidTypeHandler
```

1. 기존 Controller → ServiceImpl → Repository 인터페이스 흐름부터 읽습니다.
2. `repository/mybatis/*RepositoryMyBatisImpl`은 SQL 매퍼를 호출하고 업무 모델을 반환합니다.
3. `*Mapper`의 메서드와 같은 이름의 XML 파일(`ProductMapper.xml` 등)의 `namespace`·statement `id`를 대응시킵니다. MapStruct `*DtoConverter`와는 역할이 다릅니다.
4. 주문 저장은 평평한 `OrderRow`와 중첩 `StoredOrder`/`OrderQuote`를 변환합니다. UUID는 PostgreSQL UUID 타입으로, 시간은 Instant로 매핑합니다.
5. 검색은 `OrderSearchServiceImpl` → `OrderSearchRepository` → `OrderMapper.xml` 순서로 읽습니다.

`common/config/MyBatisConfig` 하나가 `mybatis` 프로필에서 `com.example.backend` 아래의 MyBatis `@Mapper` 인터페이스만 스캔합니다. 새 기능의 매퍼에도 `org.apache.ibatis.annotations.Mapper`를 붙이면 되므로 기능별 설정 클래스를 추가할 필요가 없습니다. MapStruct의 `org.mapstruct.Mapper`와 애너테이션 타입이 다르며 Service·Repository 인터페이스도 SQL 매퍼로 등록되지 않습니다. [MyBatis 매퍼 스캔 공식 문서](https://mybatis.org/spring/mappers.html)

SQL 결과와 Entity·HTTP DTO는 구분합니다. 다른 기능의 Java Repository·model을 직접 참조하지 않으며, JOIN은 같은 DB의 조회 projection에 한정합니다. 회원 표시명과 현재 상품명은 조회 시점 값이고, `productName`·금액은 주문 당시 snapshot입니다.

## 18-2. 프로필 선택

| lifecycle | 추가 선택 | 저장소 | GET /orders 검색 |
|---|---|---|---|
| local / test | 없음 | Memory*Repository | 없음 |
| local / test | persistence | JPA | 없음 |
| dev / staging / prod | 없음 | JPA | 없음 |
| 하나의 lifecycle | mybatis | MyBatis | 제공 |

`local,mybatis`, `test,mybatis`, `prod,mybatis`처럼 lifecycle을 하나 지정합니다. `persistence`와 `mybatis`는 동시에 선택하지 않습니다. 인증용 `oidc`는 함께 사용할 수 있습니다.

MyBatis는 JPA EntityManager·Spring Data Repository 자동 구성을 끄고 동일 DataSource의 JDBC 트랜잭션을 사용합니다. JPA/메모리 경로는 MyBatis 자동 구성과 SQL Mapper 스캔을 끕니다. DB_URL·DB_USERNAME·DB_PASSWORD, Flyway, DB readiness는 기존 DB 구성과 같습니다. migration V1과 데이터 볼륨은 변경하지 않습니다.

## 18-3. 기동과 저장소 전환

아래 명령은 **저장소 루트의 WSL Bash**에서 실행하는 학습 절차입니다. 기존 스택의 Compose 파일·profile·볼륨을 확인하고 동일 파일 조합을 유지합니다. 이미지 재빌드·Backend 재생성은 실행 중인 API에 영향을 줍니다.

기본 데모 인증 + PostgreSQL 구성에는 아래 파일 조합을 사용합니다.

```bash
bash scripts/new-local-env.sh
bash scripts/ensure-persistence-env.sh
docker compose -f docker-compose.yml -f docker-compose.persistence.yml -f docker-compose.mybatis.yml config --quiet
docker compose -f docker-compose.yml -f docker-compose.persistence.yml -f docker-compose.mybatis.yml up -d --build --wait
docker compose -f docker-compose.yml -f docker-compose.persistence.yml -f docker-compose.mybatis.yml ps
```

12단계의 `scripts/sql/learning-catalog.sql`을 사용하는 기존 명시적 데이터 준비 절차를 따릅니다. MyBatis 프로필을 켜도 학습 데이터가 자동 생성되지는 않습니다.

이미 Keycloak·관측 구성을 쓰는 레퍼런스 스택에는 단일 진입점을 사용합니다. 아래 선택은 Backend만 MyBatis로 바꾸고 인증·DB·관측 파일 조합을 유지합니다.

```bash
BACKEND_STORAGE=mybatis bash scripts/reference-stack.sh config
BACKEND_STORAGE=mybatis bash scripts/reference-stack.sh up
BACKEND_STORAGE=mybatis bash scripts/reference-stack.sh status
```

기존 JPA 레퍼런스로 돌아갈 때는 아래 명령을 사용합니다. DB 볼륨을 제거하거나 migration을 초기화하지 않습니다.

```bash
BACKEND_STORAGE=jpa bash scripts/reference-stack.sh config
BACKEND_STORAGE=jpa bash scripts/reference-stack.sh up
```

기본 데모 구성에서 종료할 때는 기동 때와 같은 세 파일 조합으로 `stop`을 실행합니다. 레퍼런스 구성은 `BACKEND_STORAGE=mybatis bash scripts/reference-stack.sh stop`을 사용합니다. 비밀값이 출력되는 `docker compose config` 대신 `config --quiet`를 사용합니다.

## 18-4. 본인 주문 조건 검색

기존 주문 생성·단건 조회 계약은 유지됩니다. MyBatis 선택 시 추가되는 Gateway 경로는 `GET /api/orders`, Backend 직접 경로는 `GET /orders`입니다. `api.read` 권한이 필요합니다.

| 쿼리 파라미터 | 의미 | 기본값·제약 |
|---|---|---|
| memberId | 주문의 업무 회원 ID | 선택, 양수 |
| productName | 주문 당시 상품명의 부분 문자열 | 선택, 최대 100자, 대소문자 무시 |
| minimumTotal | 주문 당시 단가 × 수량의 최소 금액 | 선택, 0 이상 |
| page | 0부터 시작하는 페이지 | 0, 최대 10000 |
| size | 페이지 크기 | 20, 1~100 |

응답 `data`에는 `items`, `total`, `page`, `size`가 있습니다. 정렬은 `created_at DESC, id DESC`로 고정합니다. 같은 생성 시각에서도 페이지 순서가 결정됩니다. offset 방식이므로 페이지 간 별도 요청 중 데이터가 추가되면 페이지가 이동할 수 있고, 큰 offset·부분 문자열 조건은 운영 데이터에서 실행 계획을 검토해야 합니다.

소유자는 JWT subject에서 가져오며 모든 목록/count SQL에 `owner_subject` 조건이 항상 포함됩니다. 쿼리 파라미터로 다른 소유자를 지정해도 조회 범위를 바꿀 수 없습니다. 응답에는 ownerSubject를 노출하지 않습니다.

12단계의 기본 데모 인증 `lab_api` 함수와 같은 토큰으로 주문을 두 개 만든 뒤 검색합니다.

```bash
source scripts/local-api.sh
lab_login http://localhost:8080
lab_api POST /api/orders '{"memberId":1,"productId":1,"quantity":2}'
lab_api POST /api/orders '{"memberId":2,"productId":2,"quantity":1}'
lab_api GET '/api/orders?memberId=1&productName=keyBOARD&minimumTotal=100000&page=0&size=10'
```

초기 카탈로그라면 검색 결과는 Keyboard 주문 한 건이며 totalPrice는 100000 KRW입니다. `lab_api`는 기본 데모 JWT 경로에만 사용하고, Keycloak 구성에서는 16단계에서 발급받은 access token으로 같은 URL을 호출합니다. 토큰은 출력·문서·Git에 남기지 않습니다.

## 18-5. 주문 수정·삭제 (CRUD 완성)

주문 수정·삭제는 메모리·JPA·MyBatis 모두 같은 API로 제공합니다. Gateway는 아래 경로 앞에 `/api`를 붙입니다.

| Backend 경로 | 권한 | 요청·응답 |
|---|---|---|
| PATCH /orders/{UUID} | api.write + JWT 소유자 일치 | `{"quantity":3}`, 수량 1~100 필수. 200 OrderResponse |
| DELETE /orders/{UUID} | api.write + JWT 소유자 일치 | 요청 본문 없음. 200 `data: {"id":"...","deleted":true}` |

수정은 수량만 바꾸며 회원·상품 ID, 주문 당시 상품명·단가·통화, 생성 시각을 유지합니다. 총액은 보존된 단가 × 변경한 수량으로 계산합니다. 삭제는 학습용 실제 행 삭제이며 결제 취소·환불·재고 복구 기능은 아닙니다. 없는 주문·타인 주문·삭제 후 재수정/재삭제는 모두 404 ORDER_NOT_FOUND입니다. 미인증은 401, 쓰기 권한 부족은 403, 수량 누락·범위 오류는 400입니다.

**저장소 루트의 WSL Bash**에서 새로 만든 학습 주문으로 호출합니다. 아래 절차는 기본 데모 인증용이며 Keycloak에서는 16단계 access token을 사용합니다.

```bash
source scripts/local-api.sh
lab_login http://localhost:8080
created=$(lab_api POST /api/orders '{"memberId":1,"productId":1,"quantity":2}')
order_id=$(printf '%s' "$created" | jq -er '.data.id')
lab_api PATCH "/api/orders/$order_id" '{"quantity":3}'
lab_api GET "/api/orders/$order_id"
lab_api DELETE "/api/orders/$order_id"
lab_api GET "/api/orders/$order_id" '' status  # 404
```

초기 카탈로그의 Keyboard라면 수정 후 totalPrice는 150000 KRW입니다. MyBatis 프로필에서는 목록/count도 수정된 수량·총액과 삭제 결과를 반영합니다. 기존 주문의 수정·삭제는 위 예제의 새 주문 대신 ID를 바꿔 실행하기 전에 대상을 확인합니다.

`OrderMapper.xml`의 `<update>`와 `<delete>`를 비교해 봅니다. [MyBatis XML 공식 문서](https://mybatis.org/mybatis-3/sqlmap-xml.html)

```xml
<update id="updateQuantityByIdAndOwnerSubject">
  UPDATE purchase_orders SET quantity = #{quantity}
  WHERE id = #{id} AND owner_subject = #{ownerSubject}
</update>
<delete id="deleteByIdAndOwnerSubject">
  DELETE FROM purchase_orders
  WHERE id = #{id} AND owner_subject = #{ownerSubject}
</delete>
```

소유자 조건은 조회뿐 아니라 UPDATE·DELETE 자체에도 포함하며, 영향 행 수가 1일 때만 성공합니다. 수정 전에 삭제된 주문은 INSERT로 다시 만들지 않습니다. JPA도 `@Modifying`과 같은 조건의 JPQL을 사용하고 갱신 후 영속성 컨텍스트를 정리합니다. [Spring Data JPA 변경 쿼리](https://docs.spring.io/spring-data/jpa/reference/jpa/query-methods.html#jpa.modifying-queries)

두 서비스 메서드의 `@Transactional`이 commit/rollback을 담당합니다. DB 테스트에서는 실제 UPDATE·DELETE 실행 뒤 오류를 발생시켜 원래 수량·행이 복원되는지 확인합니다. 동시 수정은 마지막으로 적용된 수량이 남는 단순 예제이며 낙관적 잠금·주문 상태·감사 이력은 추가하지 않았습니다.

## 18-6. SQL과 트랜잭션 확인

`OrderMapper.xml`의 `<if>`로 선택 조건을 구성하고 `<sql>`/`<include>`로 JOIN·필터를 목록과 count가 공유합니다. 값은 모두 `#{...}`로 바인딩하며 SQL 문자열 치환은 사용하지 않습니다. 상품명은 LIKE 대신 문자열 위치 검색을 사용해 `%`, `_`도 일반 문자로 취급합니다. [MyBatis 동적 SQL](https://mybatis.org/mybatis-3/dynamic-sql.html)

기존 `OrderServiceImpl.place`의 트랜잭션 안에서 검증·가격 계산·INSERT를 실행합니다. Mapper에서 commit/rollback을 직접 호출하지 않습니다. INSERT 뒤 실패하면 주문이 rollback되는지 공통 DB 계약 테스트로 확인합니다. 검색의 total과 items는 하나의 readOnly·REPEATABLE_READ 트랜잭션으로 같은 PostgreSQL snapshot을 읽습니다. [MyBatis Spring 트랜잭션](https://mybatis.org/spring/transactions.html)

## 18-7. 자동 검증

Windows PowerShell에서는 저장소 루트에서 다음을 실행합니다. JDK 25와 실행 중인 Docker 엔진이 필요합니다.

```powershell
.\gradlew.bat :backend:test
.\gradlew.bat :backend:databaseTest
.\gradlew.bat :backend:bootJar
```

WSL Bash에서는 저장소 Wrapper를 `bash ./gradlew`로 실행합니다. DB 테스트는 실제 PostgreSQL/Redis를 새로 만들고 종료하는 Testcontainers이며 공유 Compose 데이터에는 접근하지 않습니다. Docker가 없으면 skip하지 않고 실패합니다.

- `OrderPersistenceContractTest`: JPA/MyBatis 공통 13개 계약. HTTP 상태·인가·참조 검증·생성/수정/삭제 rollback·가격 snapshot·DB 제약·readiness·소유자 조건·삭제 후 재생성 방지.
- `OrderPersistenceIntegrationTest`: 기존 prod JPA 구성.
- `MyBatisOrderPersistenceIntegrationTest`: prod,mybatis 구성과 추가 JOIN·검색·페이징·입력·SQL 바인딩·일관된 snapshot 검증.
- `RuntimeProfileGuardTest`와 ArchUnit: 프로필 선택·기능 경계·서비스 역방향 의존·HTTP 권한 검사.

2026-10-01 검증: Backend 일반 테스트 63개와 PostgreSQL DB 테스트 22개(JPA 8개·MyBatis 14개)가 모두 통과했고 Backend 실행 JAR 빌드도 성공했습니다. MyBatis 데모·Keycloak 레퍼런스 Compose는 설정 검사만 수행했으며 실제 Compose 재배포·브라우저·Oracle·AWS/EKS 검증은 수행하지 않았습니다.

2026-10-02 수정·삭제 추가 검증: 구조 7개·서비스 8개·메모리 HTTP 11개 및 JPA 13개·MyBatis 20개 DB 테스트, 총 59개가 모두 통과했습니다. 실제 DB UPDATE·DELETE 후 오류 시 rollback, JWT 소유권·권한·수량 검증, 주문 snapshot 유지, 삭제 후 404와 검색 결과 반영을 확인했습니다. 해당 테스트에 필요한 컴파일은 성공했으며 전체 Backend 테스트·실행 JAR 빌드·Compose 재배포·브라우저·AWS/EKS 검증은 이번 작업에서 수행하지 않았습니다.

## 단계 체크

- [ ] 같은 기존 API가 JPA와 MyBatis에서 동작하는 이유를 설명했다.
- [ ] SQL Mapper와 MapStruct DTO Converter의 책임을 구분했다.
- [ ] JOIN의 현재 표시명과 주문 snapshot 값을 구분했다.
- [ ] 목록과 count 모두 JWT 소유자 조건을 적용하는지 확인했다.
- [ ] 조건 검색·페이지 경계·401/403/400·실제 INSERT rollback을 검증했다.
- [ ] 수량 수정·실제 행 삭제·소유권·UPDATE/DELETE rollback과 삭제 후 404를 확인했다.
- [ ] 로컬 PostgreSQL 검증을 Oracle·운영 배포·EKS 검증으로 표현하지 않았다.
