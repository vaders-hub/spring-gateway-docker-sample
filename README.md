# Spring Cloud Gateway 보안·Rate Limit 샘플

Java 25, Gradle 9.7.1, Spring Cloud Gateway, Redis 및 kind Kubernetes를
사용하는 로컬 학습용 예제입니다.

[AA/SWA 검토와 반영 범위](docs/aa-swa-review.md)에서 공통 응답·계층 분리·설정·로그의 설계 판단을 확인할 수 있습니다.

[학습 시스템 구성도](docs/system-architecture.md)에서 현재 Compose 구성과 이후 Kubernetes 학습 구성을 한눈에 볼 수 있습니다.

처음 시작한다면 [무료 로컬 AWS/EKS 학습 로드맵](docs/learning/README.md)을 먼저 읽으세요.
kind 배포·운영 실습 후에는 [9단계 PV·PVC와 저장·복원](docs/learning/09-storage-and-persistence.md)으로
Pod 교체 시 데이터 보존과 백업·복원을 학습할 수 있습니다.
[10단계 Spring Boot 기본 구성 검증](docs/learning/10-spring-boot-readiness.md)에서는
Stateless·설정 외부화·probe·stdout 로그·graceful shutdown을 검증하고 후속 장애 정책을 정리합니다.
실제 AWS 리소스를 만들지 않고 Compose → kind → Gateway API → 운영 실습 순서로 진행하며,
Spring Boot 설정과 Oracle/JPA/MyBatis 확장 계획도 별도로 구분합니다.

소스 공유·최초 커밋·새 PC 설정은 [Git 공유 가이드](docs/source-sharing.md)를 참고하세요.
실제 `.env`는 공유하지 않고 각 환경에서 생성합니다.

```text
Client
  │ Bearer JWT
  ▼
Gateway :8080
  ├─ JWT 서명·issuer·audience·만료·scope 검증
  ├─ X-Request-Id / X-Gateway-User 전달
  └─ Redis Token Bucket Rate Limit
         │
         ▼
Backend :8081 (외부 비공개, JWT 재검증)
```

## Docker Compose 실행

Windows VS Code·Docker Desktop을 유지하고 명령은 **WSL2 Linux Bash**에서 실행합니다.
[설치와 WSL 연결](docs/learning/00-windows-tool-installation.md)을 먼저 완료하세요.
Java와 Gradle은 Docker 빌드 이미지 안에서 사용됩니다. API 예제에는 WSL curl/jq가 필요합니다.

```bash
cd /mnt/c/dev/personal/infra/spring-gateway-docker-sample
bash scripts/new-local-env.sh
docker compose up -d --build
docker compose ps
```

첫 명령은 JWT secret과 데모 계정 암호를 무작위로 생성해 Git에서 제외된 `.env`에
저장합니다. 기존 `.env`가 있으면 변경하지 않습니다. Bash 버전에는 강제 덮어쓰기 옵션이 없습니다.

Compose는 Gateway, Backend, Redis를 같은 전용 네트워크에 생성합니다. 호스트에는
Gateway의 `127.0.0.1:8080`만 공개되며 Backend `8081`과 Redis `6379`는 공개하지 않습니다.
세 서비스 모두 Compose healthcheck를 사용합니다. `running`과 `healthy`는 구분합니다.

## VS Code / 로컬 Gradle

저장소 루트가 `gateway`, `backend`를 포함하는 Gradle 멀티프로젝트이며 Gradle
Wrapper를 포함합니다. Windows VS Code의 WSL 확장으로 저장소 루트를 열고
WSL에 JDK 25를 준비합니다. 아래 컴파일/테스트 명령은 필요할 때만 직접 실행합니다.

```bash
bash ./gradlew projects
bash ./gradlew gateway:compileJava backend:compileJava
```

기존에 폴더를 열어 둔 상태에서 import 오류 표시가 남아 있으면 명령 팔레트에서
`Java: Clean Java Language Server Workspace`를 한 번 실행해 프로젝트를 다시
불러옵니다.

## JWT 발급과 API 호출

데모 계정 값은 `.env`에서 읽고, 콘솔에는 암호를 출력하지 않은 채 토큰을 발급합니다.

```bash
set +x
source scripts/local-api.sh
lab_login http://localhost:8080
```

토큰 없이 `/api/**`를 호출하면 `401 Unauthorized`가 반환됩니다. 발급받은 토큰을
사용하면 Gateway가 요청을 Backend로 전달합니다.
두 앱 모두 `JWT_ISSUER`와 `JWT_AUDIENCE`를 검증하며, 기본 audience는 `gateway-sample-api`입니다.
GET/HEAD는 `api.read`, POST/PUT/PATCH/DELETE는 `api.write`가 필요합니다.
데모 토큰에는 두 scope가 포함됩니다. audience가 없는 기존 토큰은 다시 발급하세요.
Gateway의 `/api/actuator/**`·`/api/error/**`는 업무 토큰이 있어도 거부합니다.
Backend는 명시한 업무·관리 경로와 내부 오류 dispatch 외에는 기본 거부합니다.
새 API를 추가할 때 Controller와 함께 Backend `SecurityConfig`의 경로·메서드·scope도 등록하세요.

```bash
lab_api GET /api/hello
lab_api POST /api/echo '{"name":"gateway-test","value":25}'
```

Backend 응답의 `data.gatewayUser`와 `meta.requestId`로 인증 사용자와 요청 추적 ID를 확인할 수
있습니다. 클라이언트가 `X-Gateway-User`를 보내면 Gateway가 덮어쓰지만, Backend는
그 헤더를 신뢰하지 않고 전달된 JWT를 다시 검증해 subject를 사용합니다.

## Redis Rate Limit 확인

기본 정책은 사용자별 초당 2개 토큰 보충, 최대 burst 5개입니다. 아래처럼 빠르게
호출하면 버킷 잔량과 요청 속도에 따라 일부 요청이 `429 Too Many Requests`로 제한될 수 있습니다.

```bash
for i in {1..10}; do
  lab_api GET /api/hello '' status
done
```

정책은 `RATE_LIMIT_REPLENISH_RATE`, `RATE_LIMIT_BURST_CAPACITY` 환경 변수로
변경할 수 있습니다. Rate Limit 키는 JWT의 `sub` 값이므로 사용자별 버킷이
Redis에 만들어집니다.

## 주요 구현 위치

```text
gateway/src/main/java/com/example/gateway/config/security/
  SecurityConfig: 필터 체인과 인가 정책
  JwtConfig: JWT key/decoder/encoder Bean
  CorsConfig: Origin allowlist와 preflight 정책

gateway/src/main/java/com/example/gateway/auth/controller/AuthController.java
  HTTP 요청/응답 경계

gateway/src/main/java/com/example/gateway/auth/service/TokenService.java
  자격증명 확인과 로컬 학습용 JWT 발급

gateway/src/main/java/com/example/gateway/auth/dto/
  인증 요청/응답 계약

gateway/src/main/java/com/example/gateway/filter/RequestHeadersFilter.java
  Request ID 및 인증 사용자 헤더 전달

gateway/src/main/java/com/example/gateway/common/web/RequestIdWebFilter.java
  모든 요청의 안전한 Request ID와 완료 감사 로그

gateway/src/main/java/com/example/gateway/config/gateway/GatewayFilterConfig.java
  JWT subject 기반 Rate Limit 키 생성

gateway/src/main/java/com/example/gateway/config/properties/SecurityProperties.java
  app.security 설정 바인딩 및 시작 시 validation

gateway/src/main/resources/application.yml
  Route, Redis, RequestRateLimiter 설정

backend/src/main/java/com/example/backend/controller/
  Backend HTTP 요청/응답 경계

backend/src/main/java/com/example/backend/service/
  Backend 업무 로직

backend/src/main/java/com/example/backend/dto/
  Backend 요청/응답 계약

*/src/main/java/**/common/
  성공 envelope, 오류 코드, Problem Details, 요청 컨텍스트
```

`/auth/token`은 구조 학습을 위한 간이 발급기입니다. 운영에서는 이 엔드포인트와
공유 HS256 secret 대신 Cognito, Keycloak 같은 OIDC Provider의 issuer/JWK를
사용해야 합니다. 이 엔드포인트는 `local`, `dev`, `test` profile에서만 기본 활성화되고
`staging`, `prod`에서는 Bean 자체가 생성되지 않습니다.

## 설정과 Profile

- lifecycle profile: `local`, `dev`, `test`, `staging`, `prod` 중 정확히 하나
- deployment platform: `DEPLOYMENT_PLATFORM`으로 Docker Compose/Kubernetes 구분
- 기본값: 각 모듈의 `application.yml`
- 환경 차이: `application-{profile}.yml` 또는 배포 환경변수
- secret: Compose는 `.env`의 값을 `environment`로, Kubernetes는 Secret의 지정 키를 Deployment의 `env`로 주입. 운영 Secret Manager 연동은 후속 과제
- staging/prod CORS origin: `CORS_ALLOWED_ORIGINS` 필수 주입

주요 설정 우선순위는 명령행, JVM 시스템 속성, OS 환경변수, 외부 설정 파일,
JAR 내부 설정 파일 순입니다. staging/prod profile은 JSON 구조화 로그를 사용합니다.
Spring이 `.env`를 직접 읽는 것은 아닙니다. 환경변수와 YAML 설정을 Spring `Environment`에서
조회하고 `@ConfigurationProperties`가 붙은 설정 객체 등에 바인딩합니다.
[JWT_TTL 추적 예제](docs/learning/01-setup-and-compose.md#설정-추적-예-jwt_ttl)와
[설정 객체의 역할](docs/package-structure.md#설정-객체와-애너테이션)을 참고하세요.
현재 환경변수 방식에서는 ConfigMap/Secret을 변경해도 기존 프로세스에 자동 반영되지 않아
Pod를 교체해야 합니다. Compose의 `.env`/`environment` 변경은 `docker compose up -d`로
해당 컨테이너를 재생성해 반영합니다. `docker compose restart`만으로 환경변수가 갱신되지는 않습니다.
[Compose restart의 범위](https://docs.docker.com/reference/cli/docker/compose/restart/)

## AA 운영 기준으로 반영된 기본선

- Secret: 실제 비밀값을 소스와 Kubernetes YAML에 두지 않고 Compose `environment` 또는 Deployment의 Secret 참조로 전달
- 설정 검증: `@ConfigurationProperties`로 Gateway의 `app.security`를 `SecurityProperties`에,
  Backend의 `app.security.jwt`를 `JwtProperties`에 바인딩. `@Validated`·검증 제약·record 생성자로
  필수값과 secret 길이를 검사하고, Gateway에서는 데모 암호 길이·TTL·CORS 조건도 검증
- 환경 분리: `local`, `dev`, `test`, `staging`, `prod` 중 정확히 하나의 active profile 요구
- 내부 보안: Backend의 JWT 재검증과 Kubernetes NetworkPolicy
- Timeout: Backend connect 2초/response 5초, Redis connect 2초/command 1초
- Connection pool: Gateway Backend 연결을 최대 50개로 제한하고 acquire timeout 설정
- Server: Gateway Netty timeout/keep-alive 및 Backend Tomcat connection/thread 상한 외부화
- 장애 격리: Redis 장애 시 liveness는 유지하고 readiness만 `503`으로 내려 트래픽 제외
- 관측성: health/info와 인증된 Prometheus endpoint, Micrometer/OpenTelemetry W3C trace 전파,
  요청별 Request ID 완료 로그
- 운영 로그: staging/prod의 ECS JSON 로그와 Gateway/Backend Request ID 상관관계
- 종료: Spring graceful shutdown 20초, Compose 25초, Kubernetes 30초로 계층화
- Container: UID/GID `10001` non-root와 외부 JVM 옵션, UTC timezone
- Kubernetes: startup/liveness/readiness 분리, CPU/메모리 requests/limits,
  read-only root filesystem과 최소 권한 보안 컨텍스트

Redis 장애 시 Gateway의 readiness를 내려 Kubernetes Service의 신규 트래픽 대상에서
제외하도록 설정했습니다. 그러나 이것이 요청 단위 **fail-closed**를 보장하지는 않습니다.
현재 사용하는 Spring Cloud Gateway 5.0.3의 RedisRateLimiter는 Redis 호출 오류 때 요청을
허용하는 fallback이 있습니다. Compose 직접 접근, Pod port-forward, 기존 연결 및 probe
반영 전 구간에서는 API가 통과할 수 있습니다. 엄격한 차단은 별도 구현과 장애 테스트가 필요합니다.
[공식 RedisRateLimiter 소스](https://github.com/spring-cloud/spring-cloud-gateway/blob/v5.0.3/spring-cloud-gateway-server-webflux/src/main/java/org/springframework/cloud/gateway/filter/ratelimit/RedisRateLimiter.java)

Actuator는 `health`, `info`, `prometheus`만 노출합니다. Health와 info만 항상 익명
접근을 허용하고 Prometheus는 기본적으로 JWT 인증이 필요합니다. 별도 관측 overlay를
사용한 로컬 Compose에서만 scrape를 위해 익명 접근을 엽니다. `/env`, `/configprops`,
`heapdump`는 노출하지 않습니다.

Gateway는 허용 Origin 기반 CORS를 Spring Security보다 먼저 처리합니다. Backend는
브라우저 진입점이 아니므로 CORS를 명시적으로 비활성화했습니다. 두 서비스 모두
세션을 사용하지 않는 Bearer JWT API이므로 CSRF를 비활성화하고 Backend는 stateless
session 정책을 강제합니다.

## API 응답과 오류 규격

Controller는 HTTP 변환만 담당하고 실제 처리는 Service에 위임합니다. 요청/응답은
`Map` 대신 validation 가능한 DTO를 사용합니다. 성공 응답은 `data + meta`, 오류는
RFC 9457 `application/problem+json` 형식에 `errorCode`, `requestId`를 추가합니다.
ControllerAdvice 밖에서 발생하는 Spring Security 401/403도 동일한 오류 형태로
반환합니다. 상세 계약은 [API contract](docs/api-contract.md)를 참고합니다.

현재는 DB/JPA 의존성이 없으므로 Entity, Repository, HikariCP, `@Transactional`을
추가하지 않았습니다. DB 도입 시 Entity와 API DTO를 분리하고 Flyway,
`ddl-auto=validate`, Hikari pool/timeout/leak detection, Service 계층 transaction
경계를 함께 도입합니다. 구체적인 기준은
[Persistence introduction gate](docs/persistence-policy.md)에 정리했습니다.

OpenTelemetry span 생성과 W3C context 전파는 활성화되어 있지만 OTLP export는
기본적으로 꺼져 있습니다. Collector를 배치한 환경에서 `OTEL_EXPORT_ENABLED=true`,
`OTEL_EXPORTER_OTLP_TRACES_ENDPOINT`를 주입해 활성화합니다.

## 선택: Prometheus / Grafana

기본 Compose는 애플리케이션만 실행합니다. 로컬 관측 스택이 필요할 때 별도 overlay를
사용합니다.

```bash
bash scripts/ensure-observability-env.sh
docker compose \
  -f docker-compose.yml \
  -f docker-compose.observability.yml \
  --profile observability up -d --build
```

- Prometheus: `http://localhost:9090`
- Grafana: `http://localhost:3000`
- Grafana 사용자: `admin`
- Grafana 비밀번호: Git에서 제외된 `.env`의 `GRAFANA_ADMIN_PASSWORD`

overlay를 사용한 로컬 Compose에서만 Prometheus endpoint의 익명 scrape를 허용합니다.
kind/staging/prod 기본값은 인증 필요 상태이며, 운영에서는 전용 management network,
Prometheus Operator/ServiceMonitor 또는 조직 표준 scrape 인증을 사용합니다.

## 자동화 검증

```bash
# 기본: Bash 문법 / Kustomize 렌더링 / Compose 설정만 검사
bash scripts/verify.sh

# 명시적으로 선택할 때만 Java 컴파일과 테스트 실행
bash scripts/verify.sh test
```

`.env`가 없으면 Compose 검사를, Grafana 암호가 없으면 관측 overlay 검사를 건너뛰고
그 사실을 출력합니다. 관측 환경 준비는 `bash scripts/ensure-observability-env.sh`로 수행합니다.

기존 단위 테스트 외에 모듈별 `@SpringBootTest`와 실제 HTTP 요청을 추가했습니다.
실제 서명 JWT의 401/403/200, issuer/audience/만료/서명, 읽기·쓰기 scope,
Backend DTO 오류와 prod의 데모 발급 Bean 부재를 검증하도록 구성했습니다.
Gateway 테스트는 테스트 전용 Controller를 사용하므로 실제 프록시/Redis 장애 검증은 별도입니다.
테스트 코드 추가와 실행 성공은 다릅니다. 이번 변경에서는 컴파일·테스트를 실행하지 않았습니다.
`bootBuildInfo`와
configuration processor도 빌드 과정에서 Actuator Build Info와 IDE 설정 메타데이터를
생성합니다.

패키지별 책임과 참조 규칙은 [패키지 구조 문서](docs/package-structure.md)에,
기능 확장 시점별 추가 기준은 [AA baseline 문서](docs/aa-baseline.md)에 정리했습니다.

## kind Kubernetes

Docker Compose와 동일한 Gateway, Backend, Redis 구성을 Deployment와 Service로
옮긴 매니페스트가 `k8s` 폴더에 있습니다.

- 기본 진입: `localhost:8080` → kind NodePort `30080` → Gateway Service
- 선택 진입: `localhost:8888` → Envoy Proxy(Gateway API로 설정) → Gateway Service
- 내부 연결: Gateway → Backend Service / Redis Service
- 설정 분리: 공통 `app-config` ConfigMap / 필요한 키만 참조하는 Secret
- 구성 구조: `k8s/base` + `k8s/overlays/{local,staging}`; 루트 `k8s`는 local 호환 진입점

staging은 렌더링 학습용이며 별도 Secret/인증 제공자 준비 전에는 배포하지 않습니다.
NetworkPolicy는 [집행 검증 절차](docs/learning/08-network-policy-validation.md)를,
Redis fail-closed와 CircuitBreaker/Retry는 [후속 설계](docs/resilience-policy.md)를 참고하세요.

상세 실행 순서는 [k8s/README.md](k8s/README.md)를 참고합니다.
선택 경로의 Envoy Gateway 설치와 Gateway API 리소스는
[k8s/gateway-api](k8s/gateway-api/README.md)에 분리했습니다. 기본 배포에는 CRD가 필요하지 않습니다.

## AWS 구조와 대응

```text
로컬 / kind                    AWS + EKS
------------------------------------------------------------
Gateway JWT 검증           -> API Gateway JWT Authorizer
사용자별 Redis Rate Limit  -> 사용자별 정책 유지/이전 (AWS route throttling과 구분)
Gateway API 진입 설정     -> AWS Load Balancer Controller의 Gateway/HTTPRoute
로컬 Envoy Proxy          -> 내부 ALB (API Gateway 사용 시 VPC Link로 연결)
Kubernetes Service         -> EKS Service
Gateway/Backend Pod        -> EKS Pod
ConfigMap/Secret           -> ConfigMap/Secret 또는 Secrets Manager
```

Gateway API는 Kubernetes 네트워크 설정 표준이고 AWS API Gateway는 별도 관리형 서비스입니다.
AWS API Gateway는 필수가 아니며, 도입 시 JWT/요청 제한의 책임을 Spring Cloud Gateway와
분리해야 합니다. 상세 기준은 [진입점 설계](docs/traffic-entry.md)를 참고합니다.

## 로그와 종료

```bash
docker compose logs -f gateway
docker compose logs -f backend
docker compose logs -f redis
docker compose down
```
