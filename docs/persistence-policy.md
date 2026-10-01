# Persistence introduction gate

[12단계](learning/12-postgresql-jpa-flyway.md)에 PostgreSQL/JPA/Flyway/Testcontainers를 적용했습니다.
기본 `local`/`test`는 fixture입니다. 여기에 `persistence`를 추가하거나 `dev`/`staging`/`prod`를 사용하면
DataSource/Hikari/JPA/Flyway가 활성화되며 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`가 필수입니다.
두 저장소 구현은 같은 API 계약을 사용합니다. fixture 주문은 메모리 저장이며 재시작 시 사라집니다.
fixture는 영속성/transaction 검증을 대신하지 않습니다.
주문 저장/조회는 Service transaction, 소유권은 JWT subject, 테이블 변경은 Flyway가 담당합니다.

## 패키지와 계층

- `<feature>/model`: 불변 업무 모델과 규칙.
- `<feature>/repository`: 저장소 인터페이스. service 타입을 참조하지 않음.
- `<feature>/repository/jpa`: Entity, Spring Data `*JpaRepository`, 직접 작성한 `*RepositoryJpaImpl`.
- `<feature>/repository/fixture`: local/test 메모리 구현.
- `<feature>/web/dto`: 외부 요청/응답 계약. Entity를 반환하지 않음.
- `<feature>/service`와 `service/impl`: 업무 계약과 구현·트랜잭션 경계.
- `<feature>/web`: HTTP 매핑·검증·DtoConverter. 트랜잭션 시작 책임을 두지 않음.

다른 기능은 공개 service만 사용하며 저장소를 직접 참조하지 않습니다. ProductServiceImpl이 내부 model을 공개 ProductSnapshot으로 변환합니다. MyBatis와 빈 저장소 계층은 추가하지 않았습니다.

## Hikari 시작 기준

아래 값은 실제 DB connection 한도, Pod 수, 쿼리 latency를 반영해 부하 테스트로
확정합니다. 단순 복사해 운영값으로 사용하지 않습니다.

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: ${DB_POOL_MAX_SIZE:10}
      minimum-idle: ${DB_POOL_MIN_IDLE:2}
      connection-timeout: ${DB_CONNECTION_TIMEOUT_MS:3000}
      validation-timeout: ${DB_VALIDATION_TIMEOUT_MS:1000}
      idle-timeout: ${DB_IDLE_TIMEOUT_MS:600000}
      max-lifetime: ${DB_MAX_LIFETIME_MS:1800000}
      leak-detection-threshold: ${DB_LEAK_DETECTION_THRESHOLD:0}
  jpa:
    open-in-view: false
    hibernate:
      ddl-auto: validate
```

위 Hikari 시간 속성은 밀리초 단위 숫자입니다.
`leak-detection-threshold=0`을 운영 기본으로 두고, connection 반환 누락을 조사할 때만
예상 최장 transaction보다 큰 값(예: 60초)을 제한적으로 사용합니다. 상시 활성화는
긴 정상 쿼리를 leak으로 오인하고 로그 비용을 증가시킬 수 있습니다.

## Transaction 정책

- 주문 transaction은 public Service use case에 선언. 회원·상품 JPA 저장소에는 짧은 readOnly transaction을 두어 model 변환을 완료
- 쓰기 use case 기본 propagation은 `REQUIRED`
- 조회 use case는 `@Transactional(readOnly = true)`
- isolation은 DB 기본값을 확인한 뒤 `READ_COMMITTED`를 기본 후보로 명시
- `REQUIRES_NEW`는 감사 기록 등 외부 transaction과 생명주기를 분리해야 할 때만 사용
- Controller, DTO, Entity callback에서 transaction 시작 금지
- 같은 객체 내부의 self-invocation으로 `@Transactional` proxy를 우회하지 않음
- 외부 HTTP 호출을 DB transaction 안에서 오래 유지하지 않음

Schema 변경은 Flyway migration으로 관리하며 운영의 `ddl-auto`는 `validate` 또는
`none`만 허용합니다.
