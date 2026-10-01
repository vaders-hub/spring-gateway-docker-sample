# 11. Feature 목업과 라이브러리 적용 순서

2026-09-29. 사용자 제공 chat-session.md의 목록을 현재 Boot 4.0.8 / Java 25 소스와 대조한 계획입니다.
이번 범위는 회원·상품·주문 목업, MapStruct, OpenAPI, ArchUnit입니다.
1차에서는 **PostgreSQL**을 후속 DB로 결정했습니다. 이후 [12단계](12-postgresql-jpa-flyway.md)에 DB/JPA/Flyway/Testcontainers를 구현했습니다. 아래 1차 설명과 96개 검증 기록은 당시 범위이며, 현재 DB 저장 기능과 검증은 12단계를 참고하세요.

## 목록과 현재 상태

| 영역/라이브러리 | 현재 상태와 적용 순서 |
|---|---|
| Web / WebFlux | 기존 Backend MVC와 Gateway WebFlux 유지 |
| Jakarta Validation / Jackson | 기존 적용. 주문 요청 DTO 검증·JSON에서도 사용 |
| Actuator / Micrometer / SLF4J / Logback | 기존 적용. Prometheus/Grafana는 02단계 |
| Micrometer Tracing / OpenTelemetry | 기존 starter·전파·외부화 설정 유지. collector와 trace UI 실행 증명은 후속 |
| JUnit / AssertJ / Mockito | 기존 Boot 테스트 BOM 유지. 문서의 JUnit 5라는 이름만 보고 별도 버전을 덮어쓰지 않음 |
| MapStruct 1.6.3 | 이번 적용. Backend API DTO 변환과 컴파일 시 매핑 누락 검사 |
| springdoc-openapi 3.0.3 | 이번 적용. Boot 4.0.x용 Backend OpenAPI/Swagger UI |
| ArchUnit 1.5.1 | 이번 적용. 양쪽 모듈 패키지 의존 검사. 별도 JUnit 엔진 대신 core API 사용 |
| Spring Data JPA/JDBC + PostgreSQL | [12단계](12-postgresql-jpa-flyway.md) 적용. 우선 JPA, 복잡한 SQL 요구가 생길 때 JDBC/MyBatis 검토 |
| Flyway | [12단계](12-postgresql-jpa-flyway.md) 적용. PostgreSQL 스키마 migration과 앱 기동을 함께 검증 |
| Testcontainers | [12단계](12-postgresql-jpa-flyway.md) 적용. 실제 PostgreSQL로 migration·Repository·rollback 검증 |
| RestClient / WebClient | 3차. Backend 외부 조회는 RestClient, 비동기 스트림이 필요할 때 WebClient. Gateway의 Netty 스레드에서는 blocking 호출 금지 |
| WireMock | 3차. 외부 시스템 정상·5xx·지연 응답을 고정하여 HTTP 연동 검증 |
| Resilience4j | 3차. 읽기 호출의 CircuitBreaker·제한된 Retry부터 시작; Bulkhead/TimeLimiter는 호출 방식과 요구에 맞춰 추가 |
| Awaitility | 4차. 실제 비동기 이벤트/상태가 생길 때 도입. 동기 테스트에 sleep 대용으로 넣지 않음 |
| Lombok | 선택 보류. 현재 record와 명시적 생성자로 충분하며 Entity 도입 시 제한적으로 재검토 |
| record / switch / text block | Java 자체 기능. 필요한 코드에서 사용; 별도 라이브러리가 아님 |
| sealed class / pattern matching / virtual threads | 적합한 다형성·분기·동기 I/O 사례에서 후속 비교 실습. Gateway에 일괄 적용하지 않음 |
| Kafka | 첨부의 조건부 예시. 현재 요구/이번 범위에 없으므로 설치하지 않음 |

호환 근거: [springdoc Boot 호환표](https://springdoc.org/#what-is-the-compatibility-matrix-of-springdoc-openapi-with-spring-boot),
[MapStruct reference](https://mapstruct.org/documentation/stable/reference/html/),
[ArchUnit releases](https://github.com/TNG/ArchUnit/releases).
의존성은 버전을 고정했으며 최신 minor로 Spring Boot를 함께 올리지 않습니다.

## 현재 코드 배치

2026-10-01 구조 전환으로 Backend는 기능별 web/service/repository/model/error를 사용합니다.
Controller와 `*DtoConverter`·DTO는 web, 업무 인터페이스는 service, 구현은 service/impl에 있습니다.
JPA Entity와 Spring Data 인터페이스·저장소 구현은 repository/jpa, 메모리 저장소는 repository/fixture입니다.
MapStruct 생성 코드는 build/generated에 두며 편집하거나 Git에 추가하지 않습니다. unmappedTargetPolicy=ERROR로 응답 필드 누락을 검사합니다.

OrderServiceImpl은 MemberService.exists와 ProductService.findProduct를 사용합니다.
공개 ProductSnapshot은 product.service에 있으며 model·Entity·웹 DTO를 노출하지 않습니다.
ArchUnit은 [현재 경계](../package-structure.md)의 공개 service·역방향 의존·순환·권한을 검사합니다.

초기 1차는 조회·견적만 제공했습니다. 현재는 fixture 메모리 주문 저장과 JPA 주문 저장·본인 조회도 제공합니다.
금액은 서버 가격으로 계산하고 수량 1~100과 BigDecimal 불변식을 유지합니다. 결제·재고 차감은 미구현입니다.
fixture 주문은 재시작하면 사라지므로 내구성 학습에는 [12단계](12-postgresql-jpa-flyway.md)의 JPA를 사용합니다.

| Gateway 경로 | Backend 경로 | 권한 | 결과 |
|---|---|---|---|
| GET /api/members | /members | api.read | 고정 회원 목록 |
| GET /api/members/1 | /members/1 | api.read | 회원 1; 없는 ID는 404 |
| GET /api/products | /products | api.read | 고정 상품 목록 |
| GET /api/products/1 | /products/1 | api.read | Keyboard, 50000 KRW |
| POST /api/orders/preview | /orders/preview | api.write | 견적; 저장하지 않으므로 200 |

현재는 모든 profile에서 Controller/Service을 등록합니다. local/test 기본은 fixture, persistence 추가 또는 dev/staging/prod는 JPA입니다.
fixture에서도 주문 생성/본인 조회를 제공하지만 데이터는 메모리에만 남습니다. 아래 초기 1차 범위와 달라진 현재 구조는 [17단계](17-reference-architecture.md)를 따릅니다.
Gateway의 Redis 제한은 그대로 적용됩니다. 연속 실행으로 429가 나면 토큰 버킷 충전 후 다시 확인합니다.

기존 Compose 실행 환경에서 **새 이미지를 빌드한 뒤** 실습합니다. 아래 명령은 이 문서의 안내이며 자동 배포를 뜻하지 않습니다.
kind를 사용 중이면 먼저 [03단계](03-kind-kubernetes.md)의 이미지 갱신 절차를 적용하고,
같은 8080 포트로 Compose를 동시에 시작하지 않습니다.

```bash
source scripts/local-api.sh
lab_login http://localhost:8080
lab_api GET /api/members
lab_api GET /api/products/1
lab_api POST /api/orders/preview '{"memberId":1,"productId":1,"quantity":2}'
# data.totalPrice=100000, currency=KRW; meta.requestId와 응답 헤더 확인
lab_api POST /api/orders/preview '{"memberId":1,"productId":1,"quantity":0}'
# 400 INVALID_REQUEST
lab_api GET /api/members/999
# 404 MEMBER_NOT_FOUND
```

## OpenAPI와 Swagger UI

기본 Compose는 Backend를 호스트에 공개하지 않습니다. UI가 필요할 때만 선택 overlay를 사용합니다.
기존 .env가 준비돼 있어야 합니다. 운영 배포에 이 overlay를 포함하지 않습니다.

```bash
docker compose -f docker-compose.yml -f docker-compose.learning.yml config --quiet
docker compose -f docker-compose.yml -f docker-compose.learning.yml up -d --build
# http://localhost:18081/swagger-ui/index.html
# http://localhost:18081/v3/api-docs
```

Backend의 명세·UI 초기 화면은 local/test에서만 공개됩니다. Swagger에서 업무 API를 호출하려면
Gateway /auth/token에서 받은 JWT를 Authorize에 넣어야 합니다. 토큰을 문서·스크린샷·Git에 남기지 않습니다.
UI의 호출은 Backend 직접 경로(/members 등)이므로 Gateway의 Redis 제한은 거치지 않습니다.
정상 서비스 흐름은 위 lab_api를 사용하여 Gateway 경로로 검증합니다.
Gateway에서 명세 JSON을 읽으려면 인증된 GET /api/v3/api-docs를 사용할 수 있지만,
Gateway 프록시 경로의 Swagger UI 집계/경로 재작성은 이번에 구현하지 않았습니다.

기본 구성으로 되돌려 학습 포트를 닫을 때:

```bash
docker compose -f docker-compose.yml up -d --no-deps --force-recreate backend
```

## 다음 적용 순서와 완료 기준

1. **2차 PostgreSQL + JPA + Flyway + Testcontainers** ([12단계 구현](12-postgresql-jpa-flyway.md)): 전용 Compose DB/volume,
   환경변수 자격증명, migration V1, 회원·상품 fixture를 JPA adapter로 교체,
   주문 저장/조회와 transaction·제약·rollback·재기동 데이터 유지 검증.
   학습용 데이터를 production migration에 자동 주입하지 않습니다.
2. **3차 외부 조회 + WireMock + Resilience4j**: 예를 들어 배송비/재고 조회를 명시적 port와
   RestClient adapter로 추가. timeout → CircuitBreaker → 읽기 Retry 순으로 진행하고,
   5xx/지연/회복 시 호출 수·총 지연·공통 오류 응답을 검증합니다. 주문/결제 POST 자동 재시도는 하지 않습니다.
3. **4차 관측/비동기**: 기존 Micrometer/OTel로 추가 기능의 지표·trace를 관측하고 수집기 연결을 증명합니다.
   실제 이벤트/비동기 작업을 추가할 때 Awaitility로 완료 조건을 검증합니다.
4. **선택 비교**: Lombok, sealed class, virtual threads 등은 적용 전후 이득과 제약을 비교한 뒤 도입합니다.

Oracle/MyBatis는 기존 06단계의 별도 확장 예제로 유지하며 PostgreSQL 2차보다 먼저 적용하지 않습니다.
모든 라이브러리를 넣었다는 이유로 단계 완료로 표시하지 않고 실제 사용 코드와 검증 결과를 기록합니다.

## 검증과 단계 체크

2026-09-29 자동 검증: Backend 43개 + Gateway 53개 = **96개 통과**, 실패·오류·건너뜀 0.
MapStruct 생성/컴파일, 실제 HTTP fixture·견적·JWT·검증·오류·OpenAPI/Swagger UI 응답,
ArchUnit과 local/test 보호를 확인했습니다. Compose learning overlay는 config --quiet 검증을 통과했습니다.
브라우저 조작과 실행 중인 Compose/kind 재배포, PostgreSQL 기동은 수행하지 않았습니다.

```bash
MANAGEMENT_OTLP_METRICS_EXPORT_ENABLED=false bash ./gradlew --no-daemon \
  --project-cache-dir "$HOME/.gradle/project-caches/spring-gateway-docker-sample" \
  :backend:test :gateway:test
```

첫 실행은 새 의존성을 내려받으므로 네트워크가 필요합니다. 이후 캐시가 있으면 --offline을 사용할 수 있습니다.
자동 테스트는 임의 로컬 HTTP 포트에서 실행하며 운영 컨테이너나 실제 DB를 변경하지 않습니다.
Compose/kind 이미지 재빌드·UI 브라우저 실습·PostgreSQL 기동은 별도 실행 결과로 기록합니다.

- [ ] Controller → Service → Repository/Adapter와 domain 결과 → Mapper → 응답 흐름을 추적했다.
- [ ] DTO 검증 400, 인증 401, 권한 403, 없는 fixture 404를 구분했다.
- [ ] 견적 100000 KRW를 확인하고 실제 주문이 저장되지 않음을 설명했다.
- [ ] Swagger Backend 직접 호출과 Gateway 호출의 경로·제한 차이를 확인했다.
- [ ] ArchUnit 규칙의 금지 의존 방향과 도메인 계산 테스트의 역할을 설명했다.
- [ ] 현재 완료된 1차와 12단계 PostgreSQL 구현/후속 외부 연동 단계를 구분했다.


> 현재 구조 보완: [17단계](17-reference-architecture.md). 기능 flag 제거, 단일 주문 API, feature 경로 Bean/메서드 권한, 기능별 오류 코드, 공유 모듈과 루트 빌드를 적용했습니다. 이전 단계의 검증 수는 당시 기록입니다.
