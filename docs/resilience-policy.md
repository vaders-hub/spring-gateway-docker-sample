# 후속 장애 정책: Redis fail-closed와 CircuitBreaker / Retry

Gateway의 429·연결 실패 502·응답 timeout 504 공통 오류 처리는 구현했습니다.
RedisRateLimiter의 허용/거절 판정과 기본 오류 허용 fallback은 유지하며 `throw-on-limit`만 활성화했습니다.
Redis fail-closed, CircuitBreaker 의존성/필터와 Retry는 **아직 설계·실습 기준 단계**입니다.
기본 connect 2초 / response 5초 timeout은 존재하지만 회로 차단기와 동일하지 않습니다.

## 학습 단계와의 연결

[10단계 Spring Boot 기본 구성 검증](learning/10-spring-boot-readiness.md)에서
Stateless/종료/timeout을 같은 요청 수명으로 연결합니다. 이 문서는 장애 정책의 구현 기준을 유지합니다.

| 구현 순서 | 산출물 | 완료 근거 |
|---|---|---|
| 1 | local/test 전용 지연 fixture와 종료 검증 | 시작 확인 후 종료, 기존 요청 완료/취소 및 복구 증거 |
| 2 | Redis 실패 구분과 요청별 차단 | 429/제안 503 구분, 장애 중 Backend 미호출 |
| 3 | 프록시 오류 계약과 timeout 검증 | 연결 거절 502/응답 지연 504의 로컬 HTTP 테스트 구현; 실제 pool 고갈·Compose/kind 검증은 후속 |
| 4 | 읽기 CircuitBreaker, 제한된 Retry | 상태 전이·실제 호출 수·총 지연·부하 상한 확인 |

기본 오류 계약의 구현·검증 범위는 [API 계약](api-contract.md#시스템별-오류-처리-경계)을 참고합니다.
Redis 허용/거절만 고정한 자동 테스트는 실제 Redis 장애·분산 버킷 검증을 대체하지 않습니다.
나머지 산출물은 구현 과제이며 4단계 Redis readiness 실습 성공으로 대체하지 않습니다.

## 1. Redis 오류와 제한 초과를 구분

Spring Cloud Gateway 5.0.3 RedisRateLimiter는 Redis 오류를 내부에서 허용 응답으로
전환합니다. 따라서 바깥에서 단순 `onErrorResume`으로 감싸는 것만으로 fail-closed가
되지 않습니다. 내부에서 이미 예외를 소비했기 때문입니다.
[해당 버전 공식 구현](https://github.com/spring-cloud/spring-cloud-gateway/blob/v5.0.3/spring-cloud-gateway-server-webflux/src/main/java/org/springframework/cloud/gateway/filter/ratelimit/RedisRateLimiter.java)

| 선택 | 구현 방향 | 비용/주의 |
|---|---|---|
| 기본 fail-open 유지 | readiness 제외 + 장애 지표/경보 | 가용성 우선, Redis 장애 중 정책 초과 요청이 통과할 수 있음 |
| 엄격한 fail-closed | Redis 실행 결과를 구분하는 자체 RateLimiter/필터 | Redis가 인증 API의 가용성 의존성이 됨, 구현·회귀 테스트 필요 |
| 제한적 로컬 fallback | 인스턴스별 작은 버킷 + 장애 표시 | 전역 한도가 아님, replica 수/재시작에 따라 총 허용량 변함 |

엄격한 구현은 기존 Lua의 원자성을 유지하면서 `허용 / 한도 초과 / 저장소 오류`를
별도 결과로 전달하도록 설계합니다. 기존 응답의 음수 remaining sentinel에 의존하는
wrapper는 버전 의존성이 커서 우선안으로 삼지 않습니다. Redis PING 선조회만으로도
검사와 실제 요청 사이의 실패를 막지 못합니다.

- 실제 quota 소진: 429, 기존 rate-limit 헤더와 필요 시 정확한 Retry-After.
- Redis timeout/접속 실패로 판정 불가: 제안은 503 + `RATE_LIMIT_UNAVAILABLE`.
  이 오류 코드는 아직 enum/응답에 추가하지 않았습니다.
- 503을 기존 RateLimiter의 false로만 표현하면 기본 RequestRateLimiter가 429를 내므로
  별도의 typed exception과 Gateway 오류 writer/필터 경계를 설계해야 합니다.
- 차단 후 Backend에 요청이 전송되지 않았는지 확인합니다. Redis key, JWT, 비밀번호는
  로그/metric label에 넣지 않고 오류 유형·횟수·latency만 남깁니다.
- readiness는 신규 Service 트래픽 제어이고, 직접 접근/기존 연결의 요청별 정책은 별개입니다.

테스트: 정상 200 → quota 429 → Redis 중단/지연 시 선택 정책 → 복구 200.
Pod 직접 접근, Service 접근, 동시 요청, 복제본 2개, Redis 복구 후 버킷 초기화까지 구분합니다.

## 2. CircuitBreaker 실습 구현 순서

1. 현재 BOM과 맞는 reactive Resilience4j starter를 명시적으로 추가합니다.
   의존성 캐시에 라이브러리가 있다는 것만으로 앱에 활성화된 것이 아닙니다.
2. 학습용 읽기 route부터 별도 CircuitBreaker 이름을 부여합니다.
3. 실패율, 최소 호출 수, window 크기, open 유지 시간, half-open 허용 수,
   느린 호출 임계값을 타입 안전한 설정 또는 해당 라이브러리 설정으로 외부화합니다.
4. 전송 오류와 어떤 HTTP 5xx를 실패로 셀지 정합니다. 모든 4xx를 장애로 집계하지 않습니다.
5. fallback은 장애를 숨기는 200/빈 데이터 대신 503 + 명확한 오류 코드를 기본 제안으로 합니다.
   로컬 fallback endpoint의 보안 matcher도 명시하고 임의 외부 직접 호출을 허용하지 않습니다.
6. closed → open → half-open → closed를 metric/log와 Backend 호출 수로 확인합니다.

Backend를 중단하면 즉시 connection refused가 날 수 있어 항상 5초를 기다리는 것은 아닙니다.
`응답 지연`, `연결 거절`, `HTTP 5xx`를 별도 실험으로 구성하세요. 회로가 열리기 전의
요청과 열린 뒤의 fail-fast 요청도 분리합니다. Docker에서 Backend를 멈추는 실습을 한다면
복구 명령 `docker compose start backend`와 readiness 확인을 먼저 준비합니다.
느린 응답 fixture는 test/local 전용으로 구현하고 운영 API에 장애 스위치를 노출하지 않습니다.

[Gateway CircuitBreaker 공식 설정](https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway-server-webflux/gatewayfilter-factories/circuitbreaker-filter-factory.html)

## 3. Retry는 읽기부터, 전체 요청 예산 안에서

초기 실습은 GET/HEAD, 재시도 최대 1회, 짧은 bounded backoff처럼 작게 시작합니다.
retry 횟수 × 시도별 timeout이 전체 호출자의 timeout을 넘지 않도록 계산합니다.
필터 순서에 따라 circuit breaker가 보는 실패 횟수와 Backend 부하가 달라지므로
필터 순서까지 테스트로 고정합니다.

POST/PUT/PATCH/DELETE는 자동 재시도하지 않습니다. “멱등 메서드” 분류만 믿지 말고
실제 부작용·idempotency key·중복 방지 저장소를 확인한 뒤 선택합니다.
인증 401/403이나 quota 429를 무작정 재시도하지 않습니다.
Gateway Retry의 body caching은 메모리를 사용할 수 있습니다.
[Gateway Retry 공식 설명](https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway-server-webflux/gatewayfilter-factories/retry-factory.html)

## 완료 기준

- [ ] 가용성/엄격 차단 중 업무 요구를 선택하고 문서화했다.
- [ ] 429와 503, 원래 오류와 fallback을 구분했다.
- [ ] 장애 중 Backend 호출 수·최대 지연·메모리·재시도 횟수를 검증했다.
- [ ] 장애 제거 후 회복과 정상 인증/인가가 유지된다.
- [ ] 자동 테스트와 실제 Compose/kind 검증을 구분해 기록했다.
