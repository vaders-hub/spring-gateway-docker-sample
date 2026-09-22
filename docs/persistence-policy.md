# Persistence introduction gate

현재 Gateway와 Backend에는 DB/JPA 의존성, Entity, Repository가 없습니다. 따라서
사용되지 않는 DataSource/Hikari/transaction 설정은 활성화하지 않습니다. DB 기능을
처음 추가하는 변경에서 아래 항목을 한 묶음으로 적용합니다.

## 패키지와 계층

- `domain`: 업무 모델과 규칙
- `persistence/entity`: JPA Entity
- `persistence/repository`: Repository와 조회 구현
- `dto`: 외부 API 계약이며 Entity를 직접 참조하거나 반환하지 않음
- `service`: transaction 경계와 use case
- `controller`: HTTP mapping만 담당하며 `@Transactional` 사용 금지

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

- transaction은 public Service use case에 선언
- 쓰기 use case 기본 propagation은 `REQUIRED`
- 조회 use case는 `@Transactional(readOnly = true)`
- isolation은 DB 기본값을 확인한 뒤 `READ_COMMITTED`를 기본 후보로 명시
- `REQUIRES_NEW`는 감사 기록 등 외부 transaction과 생명주기를 분리해야 할 때만 사용
- Controller, DTO, Entity callback에서 transaction 시작 금지
- 같은 객체 내부의 self-invocation으로 `@Transactional` proxy를 우회하지 않음
- 외부 HTTP 호출을 DB transaction 안에서 오래 유지하지 않음

Schema 변경은 Flyway migration으로 관리하며 운영의 `ddl-auto`는 `validate` 또는
`none`만 허용합니다.
