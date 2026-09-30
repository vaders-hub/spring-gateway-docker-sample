# 17. 실무 레퍼런스 구조 보완

## 반영한 판단

2026-09-30 AA 검토를 반영했다. API 계약이 저장소나 학습 flag에 따라 달라지는 문제를 제거하고, 기술 공통 코드와 기능 소유 정책을 분리했다.

| 검토 | 반영 |
|---|---|
| API/application의 조건부 로딩 | 제거. 프로필은 infrastructure 저장소만 선택 |
| 두 주문 Controller | OrderController 하나에 견적·생성·본인 조회 통합 |
| common의 업무 URL/scope | feature/api의 FeatureRoutes Bean + 메서드 권한. 공통은 인증과 기본 거부 |
| 기능 오류를 common enum에 추가 | platform ErrorCode 인터페이스 + feature application/error enum |
| contract가 HTTP 오류를 강제 | MemberLookup.exists / ProductLookup.findProduct의 boolean/Optional로 변경 |
| 양쪽 common 복제 | libs/platform-core로 응답·오류 계약·TokenKey·JwtAuthorities·진단 유틸 추출 |
| 빌드 이중 구조 | 루트 settings/wrapper, version catalog, 루트 Docker context |
| query 서비스가 견적 계산 소유 | 금액 계산은 이미 OrderQuote에 존재. 카탈로그 조정 서비스만 application으로 이동 |
| 반복 입력 검사 | 서비스의 DTO와 같은 검사를 제거. DTO 경계 검증과 domain 불변식은 유지 |
| 업무 시각 | PlaceOrderService에 Clock 주입 |
| ArchUnit/test 배치 | 자기 feature domain만 허용, 명확한 패키지 패턴, 기능별 test 패키지 |

## API 호환성 변경

성공 응답은 data/meta, 오류는 Problem Details를 유지한다.

| 상황 | HTTP / errorCode |
|---|---|
| GET /members/{id} 부재 | 404 MEMBER_NOT_FOUND |
| GET /products/{id} 부재 | 404 PRODUCT_NOT_FOUND |
| GET /orders/{id} 부재 또는 다른 소유자 | 404 ORDER_NOT_FOUND |
| POST /orders 또는 /orders/preview 본문의 회원 참조 부재 | 422 INVALID_MEMBER_REFERENCE |
| 위 요청의 상품 참조 부재 | 422 INVALID_PRODUCT_REFERENCE |
| 잘못된 타입·수량·필수 입력 | 400 INVALID_REQUEST |

과거 NOT_FOUND 문자열을 비교하는 클라이언트는 위 기능 코드로 변경해야 한다.
422는 유효한 JSON/입력 형식이지만 업무 참조를 처리할 수 없는 경우에 사용한다.
Contract 자체는 이 HTTP 상태를 알지 못한다. 같은 부재라도 경로 조회는 404, 주문 참조는 422로 호출자가 결정한다.

## 프로필과 보안

local/test는 fixture, local/test+persistence 및 dev/staging/prod는 JPA이다. API 표면은 같다.
운영형 프로필에는 DB 환경변수 세 개가 필수다. 기존 staging Kubernetes 샘플을 사용할 때도 DB 연결/Secret/네트워크를 먼저 구성해야 한다.
fixture 주문 데이터는 메모리이므로 수평 확장/재시작 내구성을 검증할 때 사용하지 않는다.
기존 로컬 persistence 볼륨과 Keycloak realm은 초기화하지 않는다.

Backend는 feature 경로 Bean을 모아 인증을 요구하고 나머지를 거부한다.
권한은 HTTP handler의 메서드 보안이 소유하고 누락을 ArchUnit이 검사한다.
MVC 인자 바인딩은 메서드 호출보다 앞서므로 잘못된 본문은 권한 부족과 함께 있을 때 400이 먼저 나올 수 있다.
Gateway의 coarse api.read/api.write 검사, 관리 경로 차단, JWT 발급 모드와 로그아웃 정책은 유지한다.

## 확장 시 선택

작은 기능은 api/application/infrastructure로 시작한다. domain, contract, port, integration은 각각 업무 규칙·외부 소비자·교체 경계가 생길 때 추가한다.
현재 member/product는 다른 기능의 조회 계약과 fixture/JPA 교체를 보여주는 확장형 예제다.
Spring Modulith는 이번에 도입하지 않았다. 검증 프레임워크 교체를 구조 보완의 필수 조건으로 만들지 않는다.

## 검증 명령

저장소 루트의 WSL Bash에서 실행한다.

```bash
bash ./gradlew :libs:platform-core:test :gateway:test :backend:test
bash ./gradlew :backend:databaseTest
bash scripts/verify.sh
bash scripts/reference-stack.sh up
python3 scripts/oidc-api.py check
```

DB 테스트는 일회용 PostgreSQL/Redis와 prod 프로필을 사용한다. 운영 DB가 아니다.
서비스별 Dockerfile의 context는 루트다. 빌드에는 :backend:bootJar 또는 :gateway:bootJar를 사용한다.

## 단계 체크

- [ ] local fixture와 JPA에서 같은 Controller/API가 등록되는 이유를 설명한다.
- [ ] FeatureRoutes는 경로, 메서드 어노테이션은 권한, application은 업무 소유권을 담당함을 추적한다.
- [ ] 404 리소스 부재와 422 본문 참조 오류를 실제 응답으로 구분한다.
- [ ] TokenKey 구현이 공유 모듈 한 곳에 있으며 Servlet/WebFlux I/O 구현은 분리됨을 확인한다.
- [ ] 기본형과 확장형 중 필요한 경계를 선택하고 불필요한 전달 계층을 만들지 않는다.

설계 근거: [Spring Security 메서드 보안](https://docs.spring.io/spring-security/reference/servlet/authorization/method-security.html),
[Gradle Java Library](https://docs.gradle.org/current/userguide/java_library_plugin.html),
[Gradle 버전 카탈로그](https://docs.gradle.org/current/userguide/version_catalogs.html).


## 검증 기록 — 2026-09-30

- 격리된 소스 사본에서 platform-core 4, Gateway 62, Backend 64, PostgreSQL 6: **136개 성공, skip 0**.
- prod 프로필 DB 테스트로 API 등록, migration, 저장/소유권, rollback, 가격 snapshot 보존 검증.
- DB 테스트 첫 실행의 임시 Redis handshake 1초 timeout을 확인하고 테스트 설정만 5초로 보완한 뒤 6개 재검증 성공. 운영 timeout은 유지.
- 검증한 소스는 변경 전후 SHA-256으로 원본에 반영. 기존 DB/realm 초기화 없이 Gateway/Backend만 갱신.
- Bash/Compose/Kustomize 정적 검증과 루트 context의 양쪽 Docker 이미지 빌드 성공.
- 실제 Keycloak Client Credentials 11개: 200/401/403, 회원 404 코드, 주문 견적·생성의 잘못된 참조 422 확인. 오류 요청은 저장하지 않음.
- 실제 Chromium으로 demo/lab-admin PKCE 로그인, 일반/관리 권한, 로그아웃 후 refresh 거부 확인. 기존 Access Token은 만료까지 유효한 정책 유지.
- local-backend/local-gateway healthy. Kubernetes 재배포와 실제 운영 배포는 수행하지 않음.
