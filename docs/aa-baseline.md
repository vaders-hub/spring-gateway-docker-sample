# AA baseline and expansion gates

이 문서는 작은 학습 샘플에 이미 적용한 운영 기본선과, 기능이 생길 때 추가할
항목을 구분합니다. 아직 사용하지 않는 인프라를 미리 도입하지 않는 것이 원칙입니다.

## 현재 적용

- Java 25 / Gradle 9.7.1 Wrapper / Spring BOM 고정
- 동일 이미지에 외부 환경변수, ConfigMap, Secret을 주입하는 Build Once Deploy Many
- `local`, `dev`, `test`, `staging`, `prod` 중 정확히 하나의 lifecycle profile 요구
- lifecycle profile과 Docker/Kubernetes 배포 플랫폼 분리
- `app.security` 설정의 타입 바인딩과 시작 시 validation
- configuration processor 메타데이터와 Actuator Build Info
- Gateway와 Redis connect/response/command/acquire timeout
- 고정 크기 Gateway connection pool
- Gateway와 Backend의 JWT scope 이중 검증, 사용자별 Redis rate limit, 안전한 Request ID
- local/dev/test 전용 데모 토큰 발급기와 운영 profile 기본 비활성화
- health/info/Prometheus 최소 Actuator 노출
- startup/liveness/readiness 분리와 Redis readiness 연동
- graceful shutdown과 Kubernetes termination grace 정렬
- non-root, read-only filesystem, capability drop, resource requests/limits
- 소스와 매니페스트에서 실제 secret 제외
- Backend/Redis ingress를 Gateway Pod로 제한하는 NetworkPolicy
- 기본 NodePort와 분리된 선택형 Gateway API/Envoy 진입점 (매니페스트와 설치 절차)
- staging/prod ECS JSON 구조화 로그
- Gateway CORS allowlist와 내부 Backend CORS 비활성화 정책
- Controller/Service/DTO 분리와 성공 envelope/RFC 9457 오류 코드 규격
- Micrometer/OpenTelemetry W3C trace 전파와 외부화된 OTLP export
- 선택형 Compose Prometheus/Grafana 수집 스택과 로컬 전용 scrape 정책
- 외부화된 Netty/Tomcat timeout, connection/thread 상한
- JWT audience 검증과 읽기 `api.read` / 쓰기 `api.write` 분리
- Compose Gateway readiness healthcheck와 loopback 전용 포트
- HTTP histogram/SLO 및 환경과 분리한 application metrics tag
- Kustomize base + local/staging overlay, 명시적 Secret key 참조
- 모듈별 HTTP 보안 컨텍스트 테스트와 prod 발급 Bean 부재 테스트 코드 (실행은 별도)

## 확장 시점에 추가

| 도입 조건 | 함께 추가할 기준 |
|---|---|
| DB/JPA 도입 | Flyway, `ddl-auto=validate`, Hikari timeout/pool 근거, `open-in-view=false`, transaction 경계 |
| 외부 HTTP/AI API 도입 | 전용 WebClient, connect/response timeout, bounded pool, 제한된 retry, circuit breaker 판단, latency/error metric |
| 업무 API 증가 | API versioning, OpenAPI 계약 테스트, 도메인별 오류 코드 확장 |
| 비동기 작업 도입 | 명시적 bounded executor, queue/rejection 정책, context 전파 |
| Scheduler/Batch 도입 | 전용 thread pool, 다중 Pod 중복 실행 방지, 재실행과 실패 복구 정책 |
| 운영 인증 전환 | 로컬 `/auth/token` 제거, Cognito/Keycloak OIDC issuer/JWK, key rotation |
| 운영 관측 도입 | Prometheus ServiceMonitor, 대시보드, alert, 중앙 로그와 trace backend |
| CI/CD 도입 | test, static analysis, dependency/CVE scan, SBOM, image scan, immutable tag/digest |

## 현재 의도적 결정

- Redis 장애 때 liveness는 유지하고 readiness를 내려 Service 트래픽 대상에서 제외한다.
  다만 기본 RedisRateLimiter 5.0.3은 Redis 오류 시 허용 fallback이 있으므로 요청 단위
  fail-closed는 아직 보장하지 않는다. 직접 접근/기존 연결/반영 지연을 포함한 별도 차단 구현과
  장애 테스트가 필요하다. [상세 실습](learning/04-operations-and-recovery.md)을 참고한다.
- Backend와 Redis는 외부 포트를 열지 않는다. Gateway Compose 포트도 loopback에만 바인딩한다.
- `/auth/token`에는 아직 요청 제한이 없다. `/api/**`의 Redis 제한이 발급기를 보호하지 않는다.
  local/dev/test 학습용이며 운영 발급기는 꺼져 있다. loopback도 로컬 악성 프로세스나
  임의 port-forward까지 막지는 않는다. 공유 환경 노출 전 IP/계정별 시도 제한과
  trusted proxy 정책을 설계한다. 클라이언트가 보낸 X-Forwarded-For를 그대로 신뢰하지 않는다.
- Redis fail-closed 및 CircuitBreaker/Retry는 [별도 설계](resilience-policy.md)이며 기본 route는 유지한다.
- NetworkPolicy는 선언과 [집행 확인](learning/08-network-policy-validation.md)을 구분한다.
- 데이터가 없는 학습용 Redis이므로 persistence를 사용하지 않는다.
- Tomcat/Netty 수치는 소규모 샘플의 보호 상한이며 부하 테스트 결과로 조정한다.
- DB가 없으므로 Hikari, Entity, Repository, `@Transactional`을 만들지 않는다.
- 환경변수와 ConfigMap 변경은 자동 갱신하지 않으며 명시적인 rollout으로 반영한다.
