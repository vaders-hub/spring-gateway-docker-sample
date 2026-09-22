# 10단계: Kubernetes와 함께 검증하는 Spring Boot 기본 구성

[전체 순서](README.md) · 선수: [4단계 운영·복구](04-operations-and-recovery.md) · 관련: [9단계 저장소](09-storage-and-persistence.md)

## 목표와 상태 구분

목표는 Kubernetes 명령을 익히는 동시에 다른 프로젝트에서 참고할 Spring Boot 구성을 남기는 것입니다.
각 항목을 **설정 목적 → 코드 위치 → 실습 → 기대 결과 → 실제 결과 → 운영 조정값**으로 기록합니다.
환경변수 이름이나 예제 값을 복사한 것만으로 운영 준비가 완료된 것으로 판단하지 않습니다.

현재 프로젝트는 Boot 4.0.8, Gateway는 WebFlux/Netty, Backend는 Servlet/Tomcat입니다.
아래 현황은 소스 기준이며 이 문서 작성 중 재기동·장애 주입·부하 테스트를 실행하지 않았습니다.

| 항목 | 현재 준비된 구성 | 보완할 검증/구현 |
|---|---|---|
| Stateless | JWT, Security 세션 저장/요청 캐시 비활성화, Redis에 요청 제한 상태 공유 | 같은 JWT로 Pod 교체 전후 인증, 복제본별 인증 일관성 |
| 설정 외부화 | 환경변수 주입, `@ConfigurationProperties` 설정 객체의 값 검증, `RuntimeProfileGuard`의 환경 검사 | 값 누락·잘못된 profile 기동 실패, 동일 이미지의 환경별 실행 |
| Health probe | startup/liveness/readiness, Gateway readiness에 Redis 포함 | 의존성 장애 시 Ready 제외와 재시작 여부 대조 |
| 표준 출력 로그 | SLF4J 콘솔 출력, staging/prod ECS 형식, requestId | 로그 연결·민감값 제외, 수집기와 보존 정책은 후속 |
| Graceful shutdown | graceful, 단계별 20초, Compose 25초, k8s preStop 5초/종료 유예 30초 | 제어 가능한 지연 fixture 추가 후 처리 중 요청 완료 검증 |
| Timeout/자원 | Backend/Redis 연결·응답·pool 설정, 컨테이너 requests/limits | 계층별 시간 예산, pool 고갈·지연·연결 거절의 분리 검증 |
| 장애 정책 | Redis readiness 제외, 기본 RedisRateLimiter | 요청 단위 fail-closed, 오류 계약, CircuitBreaker/Retry는 별도 구현 |
| DB/비동기 작업 | 현재 업무 DB 및 장기 비동기 작업 없음 | 도입 시 트랜잭션·migration·작업 종료/재처리 정책 함께 추가 |

**10-1은 현재 코드로 수행할 수 있는 절차입니다. 10-3의 느린 요청 검증은 fixture 구현 전에는 수행할 수 없습니다.**
10-4~10-5는 보완할 구현과 통과 기준이며 아직 기능이 추가됐다는 의미가 아닙니다.

## 10-1. 같은 JWT로 Pod 교체 전후 인증 확인

읽을 코드: [Gateway Security](../../gateway/src/main/java/com/example/gateway/config/security/SecurityConfig.java),
[Backend Security](../../backend/src/main/java/com/example/backend/config/security/SecurityConfig.java),
[사용자 헤더 처리](../../gateway/src/main/java/com/example/gateway/filter/RequestHeadersFilter.java).

Gateway는 NoOp SecurityContext 저장소/요청 캐시를 사용하고 Backend는 STATELESS로 동작합니다.
샘플 요청의 사용자 식별은 JWT Principal에서 가져오며 헤더만 믿지 않습니다.
Stateless는 모든 상태를 없앤다는 뜻이 아닙니다. 사용자별 버킷은 Redis에 있으며
요청 처리 중의 지역 변수/연결 pool과 재시작 후에도 필요한 업무 상태를 구분합니다.

전제: 3~4단계가 정상 복구된 local kind이며 8080으로 접근 가능합니다.
실습 중 토큰이 만료되지 않도록 JWT TTL을 확인하고, 키/issuer/audience/Secret은 변경하지 않습니다.
아래는 WSL Bash의 같은 터미널에서 실행합니다. `set -x`나 토큰 값 출력은 하지 않습니다.

```bash
cd /mnt/c/dev/personal/infra/spring-gateway-docker-sample
set +x
set -euo pipefail
source scripts/local-api.sh
kubectl --context kind-gateway-lab rollout status deployment/gateway -n gateway-lab --timeout=180s
kubectl --context kind-gateway-lab rollout status deployment/backend -n gateway-lab --timeout=180s
lab_login http://localhost:8080
test "$(lab_api GET /api/hello '' status)" = 200

# cookie jar를 쓰지 않는다. 응답 헤더는 아래 검사기에만 전달하고 값 전체를 출력/저장하지 않는다.
sleep 2
printf 'header = "Authorization: Bearer %s"\n' "$LAB_TOKEN" |
  curl --config - --silent --show-error --max-time 15 --output /dev/null --dump-header - \
    "$LAB_BASE_URL/api/hello" |
  awk '
    /^HTTP\// { code=$2 }
    tolower($0) ~ /^set-cookie: *(jsessionid|session)=/ { session=1 }
    END {
      if (code != 200 || session) { print "Unexpected HTTP status or session cookie"; exit 1 }
      print "200; no JSESSIONID/SESSION cookie observed"
    }'

# 설정/이미지는 그대로 두고 각각의 Pod를 교체한다. 이 반복문 안에서 재로그인하지 않는다.
for app in gateway backend; do
  old_uids=$(kubectl --context kind-gateway-lab get pods -n gateway-lab -l "app=$app" \
    -o custom-columns=UID:.metadata.uid --no-headers | sort)
  test -n "$old_uids"
  kubectl --context kind-gateway-lab rollout restart "deployment/$app" -n gateway-lab
  kubectl --context kind-gateway-lab rollout status "deployment/$app" -n gateway-lab --timeout=180s
  new_uids=$(kubectl --context kind-gateway-lab get pods -n gateway-lab -l "app=$app" \
    -o custom-columns=UID:.metadata.uid --no-headers | sort)
  test -n "$new_uids"
  test "$old_uids" != "$new_uids"
  sleep 2
  test "$(lab_api GET /api/hello '' status)" = 200
  printf '%s: Pod changed; original JWT still accepted\n' "$app"
done
```

완료 기준: Pod UID 변경, 재로그인 없이 원래 JWT의 200, 샘플 응답에서 세션 cookie 미발급.
cookie 검사는 위 두 이름의 응답만 확인하며 앱 전체에 어떤 상태도 없음을 증명하는 검사는 아닙니다.
rollout 완료 후 호출이므로 전환 도중의 무중단 서비스나 처리 중 요청 보존도 아직 증명하지 않습니다.

401이면 만료/issuer/audience/키가 유지됐는지 확인합니다. 토큰 만료라면 해당 시도는 판정 불가로 남기고
처음부터 다시 실험합니다. 중간에 재발급하고 성공으로 기록하지 않습니다.
429는 버킷 잔량과 다른 요청을 확인하고 제한을 해제해 우회하지 않습니다.
rollout 실패 시 [4단계 복구 절차](04-operations-and-recovery.md)를 적용하고 정상 상태부터 복구합니다.

추가 검증은 4단계의 복제본 실습과 연결합니다. 두 Gateway Pod 각각으로 도착한 요청의 로그를
확인해야 복제본 간 인증 일관성을 입증할 수 있습니다. 반복 호출했다는 이유만으로 두 Pod를 거쳤다고 기록하지 않습니다.

## 10-2. 설정·probe·로그를 하나의 요청과 연결

| 읽을 파일 | 확인할 연결 | 실습과 증거 |
|---|---|---|
| [Gateway 공통 설정](../../gateway/src/main/resources/application.yml), [Backend 공통 설정](../../backend/src/main/resources/application.yml) | `RATE_LIMIT_BURST_CAPACITY` → YAML의 route 인자 → Gateway `RequestRateLimiter`/`RedisRateLimiter` 설정 | 4단계의 비밀이 아닌 burst 값 변경 전후 비교 |
| [속성 검증](../../gateway/src/main/java/com/example/gateway/config/properties/SecurityProperties.java), [profile guard](../../gateway/src/main/java/com/example/gateway/config/runtime/RuntimeProfileGuard.java) | 유효하지 않은 설정 → 기동 실패 | 4단계 profile 실패/복구; 필수값 누락 테스트는 격리된 테스트 환경에서 추가 |
| [Gateway 배포](../../k8s/base/gateway.yaml) | startup → readiness/liveness → EndpointSlice | Redis 중단 시 Ready=false, liveness 유지와 재시작 횟수 비교 |
| [요청 로그](../../gateway/src/main/java/com/example/gateway/common/web/RequestIdWebFilter.java), [Backend 로그](../../backend/src/main/java/com/example/backend/common/web/RequestIdFilter.java) | 같은 requestId의 Gateway/Backend 처리 | 2단계 로그/HTTP·route 지표 비교 |
| [운영 로그 형식](../../gateway/src/main/resources/application-prod.yml) | SLF4J → stdout → 컨테이너 로그 | ECS 설정 존재 확인; 실제 운영 profile 실행과 수집기는 별도 검증 |

`@ConfigurationProperties`는 바인딩 애너테이션이고 `SecurityProperties`/`JwtProperties`가 설정 객체입니다.
값의 조건은 `@Validated`와 검증 제약 및 record 생성자에서 검사합니다.
위 burst 값은 프로젝트의 `SecurityProperties`가 아닌 Gateway route 설정 경로를 따릅니다.
설정 객체의 추적은 [1단계 JWT_TTL 예제](01-setup-and-compose.md#설정-추적-예-jwt_ttl),
역할 구분은 [설정 객체와 애너테이션](../package-structure.md#설정-객체와-애너테이션)을 참고합니다.

`application.yml`과 기본값이 이미지에 포함되는 것은 정상입니다. 환경별 비밀값/주소를 이미지에
고정하지 않는 것이 목적이며, 현재 환경변수로 덮어쓸 수 있는 경로를 유지합니다.
ConfigMap 변경은 기존 프로세스의 환경변수를 자동 갱신하지 않습니다.

readiness의 외부 의존성 포함 여부는 서비스 정책입니다. 현재 Gateway에는 Redis를 포함하지만
Backend에는 업무 DB가 없습니다. DB 도입 시 의존성·장애 전파를 검토하고 probe 정책을 정합니다.
외부 서비스 장애를 liveness에 그대로 연결해 불필요한 재시작이 반복되지 않도록 합니다.

Spring의 콘솔 로그와 Docker/노드의 로그 보관은 별개입니다. 파일 appender를 추가해 컨테이너 내부에
쌓기보다 런타임의 회전·용량·수집 정책을 정합니다. 현재 중앙 로그 저장소는 준비되지 않았습니다.
JWT/비밀번호/본문 전체를 로그에 넣지 않고 requestId·오류 유형·소요시간으로 확인합니다.

## 10-3. Graceful shutdown의 검증 설계

현재 [Gateway Dockerfile](../../gateway/Dockerfile), [Backend Dockerfile](../../backend/Dockerfile)은
Java를 exec 형식으로 실행합니다. 두 앱에 `server.shutdown=graceful`과
`spring.lifecycle.timeout-per-shutdown-phase=20s`가 있으며 Compose 종료 유예는 25초,
Kubernetes 종료 유예는 30초입니다. Kubernetes의 preStop 5초도 종료 유예시간을 소비합니다.

20초는 종료 **단계별** 제한이므로 전체 JVM 종료시간의 보장은 아닙니다. preStop의 sleep도
endpoint 전파나 무중단을 증명하지 않습니다. graceful 설정은 임의의 비동기 작업 완료까지 자동 보장하지 않습니다.
[Spring 공식 graceful shutdown 설명](https://docs.spring.io/spring-boot/reference/web/graceful-shutdown.html),
[Kubernetes Pod 종료](https://kubernetes.io/docs/concepts/workloads/pods/pod-lifecycle/#pod-termination).

### 먼저 추가할 실습용 코드 — 현재 미구현

기존 `/hello`는 즉시 응답하므로 종료 전에 완료되어 false positive가 날 수 있습니다.
아래 조건의 지연 fixture와 검증 코드를 후속 변경에서 함께 추가한 뒤 종료 실습을 수행합니다.
아직 존재하지 않는 endpoint를 호출하는 명령은 이 문서에 제공하지 않습니다.

- local/test profile과 기본 false인 전용 enabled 설정을 함께 요구합니다. staging/prod에서는 Bean 생성 및 활성화를 차단합니다.
- 인증·scope 검증은 유지하고 지연 상한/동시 처리 수를 작게 제한합니다. 운영 API에 장애 스위치를 추가하지 않습니다.
- Gateway WebFlux 경로에서 `Thread.sleep`으로 event loop를 막지 않습니다. 각 서버 모델에 맞는 지연과 취소 처리를 선택합니다.
- requestId와 Pod 식별자를 포함한 시작/완료/취소 이벤트를 남깁니다. JWT·비밀값은 기록하지 않습니다.
- 테스트는 시작 이벤트를 기다린 다음 종료를 발생시킵니다. 고정 sleep 후 종료만으로 요청 진입을 가정하지 않습니다.
- 정상 지연 요청, 인증 실패, fixture 비활성화, profile 보호, 요청 취소 경로를 자동 테스트에 포함합니다.

### 구현 후 실행할 시나리오

| 순서 | 동작 | 판정 근거 |
|---|---|---|
| 1 | 정상 revision/replicas/image와 fixture 설정, 시간 예산을 기록 | 복구할 원래 상태가 명확함 |
| 2 | 대상 Pod의 지연 요청 시작을 관찰 | requestId·Pod·시작 시각 확인 |
| 3 | 해당 Pod만 정상 삭제하거나 해당 Compose 앱만 stop | 강제 kill/유예 0초를 사용하지 않음 |
| 4 | 기존 요청의 응답·완료 이벤트와 프로세스 종료를 확인 | 동일 requestId의 완료, 종료 유예 내 정상 종료 |
| 5 | 새 요청과 새 Pod의 Ready 상태를 따로 관찰 | 기존 요청 보존과 새 요청 라우팅을 구분 |
| 6 | fixture와 임시 timeout 설정을 원복하고 API 200 확인 | 기존 설정·인증·probe 정상 복귀 |

Backend 종료와 Gateway 종료는 각각 별도로 검증합니다. 순차 삭제와 동시 전체 종료는 결과가 다릅니다.
Compose 전체 down 중 의존 Backend가 먼저 사용 불가해지는 경우도 별도 통합 시나리오로 다룹니다.

현재 Gateway의 Backend 응답 timeout은 5초입니다. 12초 지연 요청으로 종료를 관찰하려면
실습 전용 환경에서 응답 timeout/클라이언트 timeout이 충분하도록 함께 조정해야 합니다.
Kubernetes에서는 preStop 5초 이후에도 요청이 진행 중이어야 실제 Spring drain을 관찰할 수 있습니다.
예시 설계는 지연 12초, Backend response timeout 30초, 클라이언트 40초이며 운영 권장값이 아닙니다.
Gateway가 살아 있는 Backend를 기다리는 실험과 종료 중 Backend를 호출하는 실험을 섞지 않습니다.

Pod 대상 port-forward는 대상 Pod가 종료되면 끊길 수 있습니다. 이를 앱 응답 실패와 혼동하지 않도록
동일 클러스터의 독립 클라이언트나 검증된 Service 경로를 준비하고 도착한 Pod를 로그로 확인합니다.
제한시간 초과 요청은 별도 실패 시나리오로 남기며 graceful이 무한 대기한다고 기대하지 않습니다.

## 10-4. Redis 장애와 timeout 보완 순서

세부 정책의 단일 기준은 [resilience-policy.md](../resilience-policy.md)입니다.
현재 RedisRateLimiter의 오류 허용 fallback을 readiness로 완전히 막았다고 기록하지 않습니다.
아래는 구현 목표이며 현재 오류 코드/route가 이미 이렇게 동작한다는 뜻은 아닙니다.

| 우선순위 | 추가할 내용 | 완료 기준 |
|---|---|---|
| P1 | 요청 제한의 허용/한도 초과/Redis 오류 분리 | quota=429, 판정 불가=제안 503 `RATE_LIMIT_UNAVAILABLE`; 오류 시 Backend 미호출 |
| P1 | Gateway filter/프록시 오류 계약 | ControllerAdvice 밖의 오류도 requestId/안전한 오류 코드 유지 |
| P1 | 연결 거절·응답 지연·pool 대기 초과 실습 | 각 경로의 응답 코드·최대 지연·Backend 호출 수 기록 |
| P2 | 읽기 route의 CircuitBreaker | closed/open/half-open과 회복을 지표 및 실제 호출 수로 확인 |
| P2 | 제한된 읽기 Retry | 횟수·backoff·전체 예산 고정, 쓰기 자동 재시도는 기본 제외 |
| P2 | 취소·비동기 작업·중복 처리 | 도입한 작업 유형별 종료/재처리/idempotency 계약과 테스트 |

readiness 실습은 [4-3](04-operations-and-recovery.md#4-3-redis-장애-liveness와-readiness)을 사용합니다.
Service 경로에서 접속이 안 되는 결과와 Pod 직접 접근의 HTTP 503 차단은 서로 다른 증거입니다.
엄격한 요청 단위 차단은 정상 인증 요청이 들어오는 직접 접근/기존 연결에서도 검증해야 합니다.

| 현재 설정 | 기본값 | 의미와 실습 시 주의 |
|---|---|---|
| `BACKEND_CONNECT_TIMEOUT_MS` | 2000ms | Backend TCP 연결 시도 한도; 즉시 연결 거절은 더 빨리 끝날 수 있음 |
| `BACKEND_RESPONSE_TIMEOUT` | 5s | Gateway HTTP client의 응답 대기 제한; 서비스 전체 SLA와 동일하지 않음 |
| `BACKEND_ACQUIRE_TIMEOUT_MS` | 2000ms | 연결 pool에서 자원을 얻기까지 대기 |
| `BACKEND_MAX_CONNECTIONS` | 50 | 인스턴스당 pool 상한; replica 증가 시 총 연결 수 증가 |
| `REDIS_CONNECT_TIMEOUT` / `REDIS_COMMAND_TIMEOUT` | 2s / 1s | Redis 연결/명령 경로의 제한 |
| `lab_api`의 curl max-time | 15s | 현재 학습 클라이언트의 요청 한도 |

계산할 전체 예산은 pool 대기·연결·응답·재시도 backoff·호출자 제한을 포함합니다.
각 설정이 모든 구간을 단순 합산해 항상 같은 시점에 종료시킨다고 가정하지 않고 실제 지연을 측정합니다.
503과 504 등 외부 오류 계약은 실패 유형별로 정한 뒤 테스트하며, 현재 프록시 오류가 모두 공통 JSON이라고 가정하지 않습니다.

## 10-5. 추후 참고용 설정 관리표

프로젝트를 확장할 때 이 표의 빈칸을 채웁니다. Secret 값은 기록하지 않고 변수명과 주입 경로만 남깁니다.
학습용 숫자를 운영 권장값으로 승격하려면 부하·장애·복구 근거가 필요합니다.

| 주제 | 설정/코드 근거 | 조정할 기준 | 상태 |
|---|---|---|---|
| 인증/세션 | 두 SecurityConfig, JwtConfig | 토큰 수명·발급자·키 교체·권한 | 기본 구현, 10-1 실습 대기 |
| 설정 검증 | `SecurityProperties`/`JwtProperties`의 값 검증, `RuntimeProfileGuard`의 환경 검사 | 필수값·허용 범위·환경별 금지값 | 기본 구현, 확장 속성별 검증 추가 |
| 가용성 | Actuator group, k8s probes | 의존성 정책·기동시간·실패 임계값 | 기본 구현, 4단계 장애 실습 |
| 종료 | shutdown timeout, preStop, 종료 유예 | 최대 처리시간·종료 단계·작업 유형 | 기본 구현, 10-3 fixture/검증 미구현 |
| 로그/관측 | SLF4J, ECS, metrics, requestId | 민감값·label 수·보존량·수집 장애 | 부분 구현, 중앙 수집/trace 저장소 후속 |
| 외부 연동 | Gateway HTTP pool/Redis timeout | 동시성·호출자 예산·서비스 허용 부하 | 기본 설정, 10-4 오류 계약/차단 후속 |
| 데이터 | [영속성 도입 기준](../persistence-policy.md) | migration·트랜잭션·backup/RPO/RTO | DB 미구현, 9단계는 파일 저장 실습 |

재사용 시 체크: 변경 이유, 설정 기본값, 환경변수/Secret 주입 위치, 잘못된 값의 실패 방식,
자동 테스트 결과, 실제 Compose/kind 검증 결과, 복구 방법을 같은 변경에 남깁니다.
“문서 준비 / 코드 구현 / 자동 테스트 통과 / 실제 실습 확인” 상태를 따로 기록합니다.

## 구조·코드 이해 체크

- [ ] Stateless 인증 설정과 앱 전체의 업무 상태 관리가 다른 범위임을 설명한다.
- [ ] 재로그인 없이 Pod 교체 후 같은 JWT로 성공했고 토큰 만료 등 판정 불가 조건을 구분했다.
- [ ] 환경별 값을 이미지에 고정하지 않는 이유와 기본 application.yml의 역할을 설명한다.
- [ ] readiness 제외와 요청 단위 fail-closed 차이를 설명한다.
- [ ] stdout 로그, 런타임 로그 보관, 중앙 수집, metrics, traces의 준비 상태를 구분한다.
- [ ] 종료 단계별 timeout, preStop, 컨테이너 유예, 클라이언트 timeout의 관계를 설명한다.
- [ ] graceful 지연 fixture가 현재 미구현임을 알고 시작/완료/취소 증거를 포함한 검증 계획을 세웠다.
- [ ] Redis quota와 장애, 연결 거절과 응답 지연, pool 대기와 서버 처리시간을 구분한다.
- [ ] 재시도 횟수/부하/중복 쓰기 위험을 반영하고 업무 상태·비동기 작업 도입 시 검증 항목을 확장한다.
- [ ] 설정 존재·코드 구현·자동 테스트·실제 실행 상태를 따로 기록했다.

학습을 마쳤으면 `unset LAB_TOKEN LAB_BASE_URL`로 토큰을 해제합니다.
재기동 실습은 image/profile/Secret을 변경하지 않지만 rollout restart annotation/revision은 남습니다.
추가 실습에서 바꾼 replica/환경값이 있다면 원래 상태로 복구하고 API 200을 확인합니다.
다음 구현은 **10-3 지연 fixture와 종료 검증 → 10-4 요청 단위 Redis 장애 정책/timeout → CircuitBreaker/Retry** 순서입니다.
