# 19. Kafka 주문 이벤트 기본 예제

Gateway는 기존 HTTP 인증·라우팅을 유지하고, Backend만 Kafka producer/consumer를 사용한다.
Kafka 브로커는 Backend 프로세스와 별도로 실행한다. 이 단계는 로컬 학습·레퍼런스이며 AWS/MSK/EKS 리소스를 만들지 않는다.

## 19-1. 흐름과 파일

```text
POST /orders · PATCH /orders/{id} · DELETE /orders/{id}
  → OrderServiceImpl → Repository 변경 → Spring OrderEvent
  → DB commit 후 OrderEventProducer (메모리 모드는 즉시)
  → backend.orders.v1 (key = orderId, JSON 문자열)
  → OrderEventConsumer → 로그로 후속 처리 예제
  → 처리 실패: 1초 간격 재시도 2회 → backend.orders.v1.DLT
```

| 위치 (Backend 기준) | 책임 |
|---|---|
| `order/messaging/OrderEvent` | `eventId, orderId, type, quantity, occurredAt` 계약. JWT·소유자·회원 개인정보를 포함하지 않는다. |
| `order/messaging/OrderEventProducer` | `@TransactionalEventListener(AFTER_COMMIT, fallbackExecution = true)`로 커밋 후 발행. |
| `order/messaging/OrderEventConsumer` | `@KafkaListener`로 JSON과 주문 key를 검증하고 로그 처리. 실제 알림·DB 갱신은 후속 확장이다. |
| `common/config/KafkaConfig` | 주문 토픽·DLT 생성, 재시도·복구 공통 설정. 업무 이벤트 클래스를 참조하지 않는다. |
| `common/config/properties/KafkaMessagingProperties` | 토픽 이름·파티션·복제 수 검증. |
| `application-kafka.yml` | 브로커, serializer, consumer group, ack·timeout 설정. |
| `docker-compose.kafka.yml` | Apache Kafka 4.2.2 단일 노드 KRaft, healthcheck, named volume. ZooKeeper 불필요. |

견적·조회와 실패한 수정/삭제는 이벤트를 만들지 않는다. 수량은 생성·수정에만 있고 삭제에는 null이다.
`backend.orders.v1`은 이벤트 계약 버전이다. 호환되지 않는 변경은 새 버전 토픽/계약을 검토한다.

## 19-2. 선택 프로필과 주소

Kafka 없이 기존 실행 방식이 기본이다. `kafka` 프로필을 추가하면 producer, consumer, 토픽 생성이 활성화된다.
DB 저장 방식과 인증 선택은 독립적이다.

| 활성 프로필 예 | 저장 방식 |
|---|---|
| `local,kafka` | 메모리 |
| `local,persistence,oidc,kafka` | JPA + Keycloak |
| `local,mybatis,oidc,kafka` | MyBatis + Keycloak |

공통 설정은 Kafka 토픽 자동 생성·listener 시작을 꺼 둔다. Kafka 프로필에서는 브로커에 연결해
토픽과 DLT를 생성하며 브로커가 없으면 시작을 실패시킨다.
로컬 구성은 `PLAINTEXT`이며 호스트 공개 포트는 `127.0.0.1:9092`로 제한한다.
운영에는 TLS/SASL·ACL, 여러 브로커·복제·보관 정책 등을 따로 설계해야 한다.

| 설정 | 기본값 / 의미 |
|---|---|
| `KAFKA_BOOTSTRAP_SERVERS` | IDE/호스트 `localhost:9092`, Compose Backend `kafka:19092` |
| `KAFKA_CONSUMER_GROUP` | `backend-order-learning`. 같은 그룹의 Backend 복제본은 파티션을 나눠 소비한다. 다른 업무 구독자는 별도 그룹을 쓴다. |
| `KAFKA_ORDER_TOPIC` | `backend.orders.v1` |
| `KAFKA_TOPIC_PARTITIONS` | 1. 주문 key를 써서 같은 주문을 같은 파티션으로 전달한다. |
| `KAFKA_TOPIC_REPLICAS` | 1. 로컬 브로커 한 개에 맞춘 값이며 운영 내구성 설정이 아니다. |

브로커는 접속 후 advertised listener 주소를 알려 준다. 컨테이너에서 `localhost:9092`를 쓰면
Backend 자신을 가리키므로 내부 주소를 따로 사용한다. 파티션 수 변경은 key 배치에 영향을 주며,
여러 HTTP 요청/여러 producer 사이의 업무 변경 순서를 자동으로 보장하지 않는다.

## 19-3. Keycloak·DB 레퍼런스 구성에 추가

저장소 루트의 **WSL Bash**에서 실행한다. 기존 `scripts/reference-stack.sh`가
Keycloak·DB·관측 구성과 볼륨을 유지하면서 Kafka overlay를 마지막에 합친다.
기존 스택의 `BACKEND_STORAGE` 선택을 계속 사용하고 같은 프로젝트에서 상태·정지를 실행한다.
`.env`가 없으면 먼저 `bash scripts/reference-stack.sh prepare`로 기존 자격증명을 준비한다.

```bash
# JPA + Keycloak + 기존 관측 구성 + Kafka
BACKEND_MESSAGING=kafka bash scripts/reference-stack.sh config
BACKEND_MESSAGING=kafka bash scripts/reference-stack.sh up
BACKEND_MESSAGING=kafka bash scripts/reference-stack.sh status

# MyBatis를 사용하던 경우
BACKEND_STORAGE=mybatis BACKEND_MESSAGING=kafka bash scripts/reference-stack.sh config
BACKEND_STORAGE=mybatis BACKEND_MESSAGING=kafka bash scripts/reference-stack.sh up
BACKEND_STORAGE=mybatis BACKEND_MESSAGING=kafka bash scripts/reference-stack.sh status
```

`config`는 비밀값을 출력하지 않는 정적 검사다. `up`은 앱 이미지 빌드와 컨테이너 기동/재생성을 수행한다.
Kafka는 자체 JVM과 볼륨을 사용하므로 PC 메모리·디스크 여유를 확인한다.
기존 9092 포트를 다른 서비스가 쓰면 먼저 충돌을 해결한다.

기본 데모 인증 + 메모리 예제만 새로 실행할 경우에는 아래 파일 조합을 쓴다.
이미 Keycloak/DB 스택을 실행 중이라면 위 레퍼런스 스크립트를 사용한다.

```bash
docker compose -f docker-compose.yml -f docker-compose.kafka.yml config --quiet
docker compose -f docker-compose.yml -f docker-compose.kafka.yml up -d --build --wait --wait-timeout 300
```

IDE로 Backend를 실행할 때는 이 overlay에서 `kafka` 서비스만 기동하고 기존 JWT/Redis 설정과
`local,kafka` 프로필을 지정한다. 이때 bootstrap 주소는 `localhost:9092`다.

## 19-4. 이벤트 확인과 복구

기존 인증 방식으로 로그인하고 [주문 API](../api-contract.md)를 호출한다.
데모 인증은 `scripts/local-api.sh`, Keycloak 인증은
[16단계](16-keycloak-and-method-security.md)의 토큰 발급 절차를 사용한다.
비밀값·토큰은 로그나 Git에 남기지 않는다.

생성 → 수량 수정 → 삭제 후 아래 로그에서 같은 `orderId`와 각각 다른 `eventId`를 확인한다.

```bash
docker compose logs --since 5m backend | grep 'Kafka order event'
# 토픽/파티션 상태와 DLT 원문 확인 (공유 화면에 실제 업무 데이터 출력 금지)
docker compose exec kafka /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server kafka:19092 --describe --topic backend.orders.v1
docker compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server kafka:19092 --topic backend.orders.v1.DLT \
  --from-beginning --timeout-ms 10000 --property print.key=true
```

Backend 소비자는 `enable-auto-commit=false`, record ack를 사용한다.
일시적 처리 오류는 최초 시도 + 재시도 2회 후 DLT로 복구한다.
잘못된 JSON/계약/key는 재시도 없이 DLT로 이동한다.
DLT 발행이 실패하면 원본 처리를 완료로 넘기지 않고 다시 처리하게 한다.
DLT는 별도 소비자로 자동 재처리하지 않는다. 실패 원인·중복 영향 확인 후 선택적으로 재처리하는 후속 기능이다.

정지에는 실행했던 조합을 유지한다. 아래는 JPA 레퍼런스 예이며 MyBatis라면 같은 변수도 추가한다.

```bash
BACKEND_MESSAGING=kafka bash scripts/reference-stack.sh stop
```

`stop`은 볼륨을 보존한다. `down -v`는 사용하지 않는다. Kafka를 켰던 스택을 정지할 때
`BACKEND_MESSAGING=kafka`를 빠뜨리면 Kafka 서비스가 조합에서 빠져 그대로 남을 수 있다.

## 19-5. 기본 예제의 전달 보장 경계

- DB 트랜잭션 rollback에는 Kafka로 발행하지 않는다. 메모리 모드에는 DB 트랜잭션이 없으므로 즉시 발행한다.
- **DB commit과 Kafka 발행은 하나의 원자적 작업이 아니다.** 커밋 직후 프로세스 종료·발행 실패로 이벤트가 누락될 수 있다.
  API 성공은 저장 성공이며 Kafka 소비 완료를 뜻하지 않는다. 발행 실패는 ID와 오류 타입을 로그로 남긴다.
- producer의 `acks=all`과 idempotence는 브로커 송신 재시도 중 중복을 줄인다. DB와 Kafka 전체의 exactly-once를 보장하지 않는다.
- consumer에는 재전달이 가능하다. 이 예제는 로그만 처리하며 영구 중복 방지는 구현하지 않았다.
  실제 DB 변경·알림을 붙이려면 `eventId`를 저장해 중복 처리를 방지한다.
- 기본 readiness에 Kafka 연결 상태는 포함하지 않는다. 시작 후 브로커 장애 때도 HTTP 저장이 성공할 수 있다.
  업무 요구에 따라 readiness 정책·발행 실패 지표/알림·consumer lag를 보완한다.
- 다음 단계는 Transactional Outbox(DB 주문 변경+이벤트 저장을 같은 트랜잭션으로 처리),
  발행 worker/재발행, 영구 중복 방지, DLT 재처리, Schema 호환성 관리다.

## 19-6. 자동 검증

JDK 25·Docker가 필요하다. 기존 Compose와 무관한 일회용 Kafka Testcontainer를 사용한다.

```bash
bash ./gradlew :backend:test :backend:kafkaTest
# 기존 DB 계약까지 같이 확인할 경우
bash ./gradlew :backend:databaseTest
# 스크립트 진입점 (정적 검사 포함)
bash scripts/verify.sh kafka
```

기본 `test`는 `database`와 `kafka` 태그를 제외해 Docker 없이 실행한다.
`kafkaTest`는 실제 브로커의 생성·수정·삭제 소비, 트랜잭션 commit/rollback에 따른 발행,
일시적 오류 재시도, 잘못된 JSON과 반복 실패의 DLT 이동 및 후속 정상 메시지를 검증한다.
트랜잭션 발행 테스트는 Spring 트랜잭션 동기화를 사용하며 DB 저장 rollback 자체는 `databaseTest`에서 별도로 검증한다.
이 자동 검증을 Compose 전체 배포·장애 복구나 실제 AWS/MSK 운영 검증으로 표현하지 않는다.

### 구현 시 검증 기록 (2026-10-02)

- Java 25에서 Backend 일반 테스트 70개, PostgreSQL/JPA·MyBatis 테스트 33개, Kafka 테스트 5개 통과.
- Kafka 테스트는 Apache Kafka 4.2.2 일회용 브로커에서 실제 발행·소비·재시도·DLT를 확인했다.
  수동 트랜잭션 테스트는 메모리 프로필에 리스너 factory만 보완해 commit 전 미발행과 rollback 미발행을 확인했다.
- 메모리 및 JPA/MyBatis + Keycloak·관측 + Kafka Compose 조합의 정적 검사를 통과했다.
  WSL에서 레퍼런스 스크립트의 JPA/MyBatis Kafka 옵션과 Bash 문법을 검사했다.
- 기존 Compose를 기동·재배포하지 않았다. 전체 스택 API, 브로커 장애·복구, 운영 보안·AWS/MSK는 미검증이다.

## 참고

2026-10-02에 공식 자료와 현재 Boot 4 구성을 대조했다.

- [Spring Boot Kafka 자동 구성](https://docs.spring.io/spring-boot/4.0/reference/messaging/kafka.html)
- [Spring Kafka 시작과 Boot Starter](https://docs.spring.io/spring-kafka/reference/quick-tour.html)
- [Spring Kafka 오류 처리·DLT](https://docs.spring.io/spring-kafka/reference/4.0/kafka/annotation-error-handling.html)
- [Apache Kafka 공식 이미지·다운로드](https://kafka.apache.org/community/downloads/)
- [Testcontainers Kafka 모듈](https://java.testcontainers.org/modules/kafka/)
