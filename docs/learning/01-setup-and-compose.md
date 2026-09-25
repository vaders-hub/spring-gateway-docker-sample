# 0~1단계: 환경 준비와 Compose로 애플리케이션 이해

[전체 순서](README.md) · 다음: [관측성](02-observability.md)

## 목표 / 구현 위치

Kubernetes에 올리기 전에 앱 자체를 이해합니다. `docker-compose.yml`, 각 모듈의
`Dockerfile`, `gateway/src/main/resources/application.yml`, `auth/controller`,
`auth/service`, Backend의 `controller/service/dto`를 함께 봅니다.

컨테이너 엔진은 Windows Docker Desktop, 아래 명령은 WSL Bash에서 실행합니다.
API 실습에는 curl/jq/OpenSSL이 필요합니다. IDE 개발은 WSL JDK 25가 필요하며 Gradle은
저장소 Wrapper를 사용합니다. kind/kubectl/Helm은 3·5단계 전에 준비하면 됩니다.

### 명령 주석 읽는 방법

`#`로 시작하는 줄은 설명이므로 명령과 함께 복사해도 실행되지 않습니다.
`[조회]`는 상태 확인, `[준비]`는 로컬 파일/셸 준비, `[기동]`은 컨테이너 생성·실행,
`[호출 확인]`은 실제 HTTP 요청입니다. 실행 전에 대상과 기대 결과를 먼저 읽습니다.
코드 블록은 위에서부터 순서대로 실행하고, 실패하면 다음 명령 전에 원인을 확인합니다.
줄 끝의 `\`는 다음 줄까지 하나의 명령이라는 뜻이므로 뒤에 공백이나 주석을 붙이지 않습니다.

## 0-1. 도구 설치와 확인

처음 설치하거나 `kind`/`helm` 명령을 찾지 못한다면
[Windows + WSL2 기본 도구 설치 가이드](00-windows-tool-installation.md)를 먼저 진행합니다.
WSL2, Docker Desktop·Compose, kubectl, kind, Helm, JDK 25, Gradle Wrapper의
설치/확인 명령과 PATH 문제 해결을 정리했습니다.

1. Windows 가상화/WSL2 지원을 확인하고 Docker Desktop을 설치합니다.
2. Docker를 Linux 컨테이너 모드로 기동합니다. Docker Desktop의 자체 Kubernetes는
   이 과정에서 사용하지 않으므로 별도로 켤 필요가 없습니다.
3. Docker Desktop의 WSL Integration을 개발용 배포판에 켭니다.
4. kind/kubectl/Helm은 Linux용 도구를 WSL에 설치합니다. 저장소 루트로 이동합니다.

```bash
# [조회] CLI(Client)와 엔진(Server) 버전 확인. Client만 나오면 Docker 연결부터 해결합니다.
docker version
# [조회] 엔진의 컨테이너 운영체제만 출력. 기대값은 linux입니다.
docker info --format '{{.OSType}}'
# [조회] Compose 사용 가능 여부와 버전 확인. 컨테이너를 생성하지 않습니다.
docker compose version
# [조회] PATH에서 찾은 실행 경로를 모두 표시. 맨 위 경로가 우선이며 중복 자체는 설치 실패가 아닙니다.
type -a docker kubectl kind helm
# [조회] API 호출/JSON 처리/비밀값 생성에 사용할 도구의 실행 경로 확인.
command -v curl jq openssl
# [조회/3단계 전] 로컬 Kubernetes 생성 도구인 kind의 버전 확인.
kind version
# [조회/3단계 전] kubectl 클라이언트만 확인. 아직 클러스터에 연결하거나 만들지 않습니다.
kubectl version --client
# [조회/5단계 전] Envoy 설치에 사용할 Helm의 버전 확인.
helm version
```

`docker version`에서 Server 접속까지 성공하고 OSType이 `linux`여야 합니다.
없는 도구는 이 문서 작성 과정에서 자동 설치하지 않았습니다.
실습용 시작 권장량은 Docker에 CPU 4개/메모리 8GiB 정도이며 성능 보장값은 아닙니다.
RAM 여유가 작으면 관측 스택·kind·Oracle을 동시에 실행하지 않습니다.

공식 설치 자료:

- [Docker Windows 설치](https://docs.docker.com/desktop/setup/install/windows-install/)
- [kind Quick Start](https://kind.sigs.k8s.io/docs/user/quick-start/)
- [kubectl Linux 설치](https://kubernetes.io/docs/tasks/tools/install-kubectl-linux/)
- [Helm 설치](https://helm.sh/docs/intro/install/)

## 0-2. 포트와 비밀값 준비

```bash
# [준비] 이후 상대 경로가 저장소 기준으로 해석되도록 이동합니다.
cd /mnt/c/dev/personal/infra/spring-gateway-docker-sample
# [조회] WSL의 TCP 수신 대기 포트 확인. -l: listen, -t: TCP, -n: 숫자로 표시.
ss -ltn
# [조회] 실행 중인 Docker 컨테이너의 이름/포트만 확인. 다른 프로젝트의 포트 점유 여부를 봅니다.
docker ps --format 'table {{.Names}}\t{{.Ports}}'
# [준비/파일 생성] 랜덤 JWT 키·데모/Grafana 비밀번호를 .env에 저장. 실제 값은 출력하지 않습니다.
# 기존 .env가 있으면 변경하지 않습니다. 이 명령은 앱을 실행하지 않습니다.
bash scripts/new-local-env.sh
# [검증] Compose 설정과 필수 변수 해석 확인. --quiet는 비밀값이 펼쳐진 설정 전체의 출력을 막습니다.
docker compose config --quiet
```

`ss`는 WSL 측 포트 조회입니다. Windows 측 점유는 [문제 해결 문서](07-troubleshooting-and-cleanup.md)를 참고합니다.
포트 사용자가 있으면 먼저 소유 프로젝트를 확인합니다. 프로세스를 무조건 종료하지 않습니다.
생성 스크립트는 `.env`에 랜덤 JWT secret/데모 비밀번호를 저장합니다. 기본적으로
기존 파일을 덮어쓰지 않습니다. Bash 버전에는 강제 덮어쓰기 옵션을 제공하지 않습니다.
`.env`는 Git 제외 대상이지만 암호화된 보관소는 아닙니다.

현재 Compose의 `127.0.0.1:8080:8080`은 Gateway를 호스트 loopback에 바인딩합니다.
Backend/Redis는 호스트 포트를 공개하지 않으며 같은 Compose 네트워크에서 서비스 이름으로 접근합니다.
kind의 8080과 관측 포트도 각각 loopback 바인딩 설정을 사용합니다.

## 1-1. 기동

```bash
# [기동] 이미지를 빌드(--build)하고 컨테이너를 생성/필요 시 재생성해 백그라운드(-d) 실행합니다.
# up 성공만으로 모든 앱이 healthy라고 가정하지 않고 아래 상태/HTTP 확인까지 수행합니다.
docker compose up -d --build
# 직전 명령의 종료 코드($?)가 0이 아니면 현재 셸을 종료. 이 사이에 다른 명령을 넣지 않습니다.
if [ "$?" -ne 0 ]; then printf '%s\n' 'Compose startup failed.' >&2; exit 1; fi
# [조회] 이 Compose 프로젝트의 컨테이너 상태와 health/포트 확인.
docker compose ps
# [조회] 세 서비스의 최근 로그 80줄씩 확인. 공유 전에 비밀값 유무를 확인합니다.
docker compose logs --tail 80 gateway backend redis
# [호출 확인] Gateway의 요청 수신 준비 상태 확인. 기대값은 HTTP 200입니다.
# -sS: 진행 표시 숨김/오류 표시, -o /dev/null: 본문 버림, -w: 상태 코드만 출력.
curl -sS -o /dev/null -w '%{http_code}' http://localhost:8080/actuator/health/readiness
```

기대 결과: Gateway·Backend·Redis 모두 healthy, 최종 readiness 200.
Gateway healthcheck도 Redis를 포함한 readiness를 조회합니다. `running`만으로 준비 완료를 판단하지 않습니다.
Gateway 호스트 포트는 `127.0.0.1:8080`에만 바인딩합니다.
첫 빌드는 JDK/Gradle/의존성을 내려받으며 컴파일도 수행합니다. `bootJar` 성공이
전체 테스트 통과를 의미하지는 않습니다. 빌드 실패를 Kubernetes 문제로 넘기지 않습니다.

## 1-2. JWT 발급 및 정상 API

```bash
# [보안] 셸 명령 추적을 꺼 비밀번호/JWT가 확장 출력되지 않게 합니다.
set +x
# [준비] 로그인/API 호출 함수를 현재 셸에 로드. 별도 서버나 컨테이너를 띄우지 않습니다.
source scripts/local-api.sh
# [인증 호출] .env 자격증명으로 데모 JWT 발급. 토큰과 호출 주소를 현재 셸 변수에 보관합니다.
lab_login http://localhost:8080
# [호출 확인] 로그인 성공 후 저장한 JWT로 hello 조회. 정상 응답의 사용자/요청 ID를 확인합니다.
lab_api GET /api/hello
# [호출 확인] JSON 본문을 보내 echo 처리 확인. 유효 토큰/요청과 버킷 여유가 있으면 200입니다.
lab_api POST /api/echo '{"name":"learning","value":25}'
```

`local-api.sh`는 `.env`를 데이터로 읽으며 `source .env`로 실행하지 않습니다.
인증 본문/헤더는 curl의 표준 입력으로 전달해 프로세스 인자에 비밀번호/JWT를 넣지 않습니다.
`.env`의 데모 자격증명은 생성 스크립트 형식인 따옴표 없는 `KEY=value`를 사용합니다.
`set -x`, `curl -v`, 토큰 변수 출력은 사용하지 않습니다. 새 터미널에서는 source/login부터 반복합니다.
토큰/로그인 응답 전체를 출력하지 않습니다. 정상 응답의 `data.username`,
`meta.requestId`와 응답 헤더 `X-Request-Id`가 일치하는지 확인합니다. 추적 ID는 업무 `data`에 중복 저장하지 않습니다. Gateway는 `/api`를 제거해 Backend의
`/hello`, `/echo`로 전달하며, Backend도 JWT를 검증합니다.

## 1-3. 실패와 요청 제한 확인

```bash
# [실패 확인] Authorization 없이 요청. 인증 누락 401을 예상합니다. LAB_BASE_URL은 로그인 때 설정됩니다.
curl -sS -o /dev/null -w '%{http_code}\n' "$LAB_BASE_URL/api/hello"
# [실패 확인] -H로 잘못된 인증 헤더를 추가. 실제 JWT가 아닌 invalid-token에 대해 401을 예상합니다.
curl -sS -o /dev/null -w '%{http_code}\n' -H 'Authorization: Bearer invalid-token' "$LAB_BASE_URL/api/hello"

# [제한 확인] 인증된 요청을 순차적으로 20번만 전송. 버킷 초과 시 429가 섞이는지 확인합니다.
# lab_api의 ''는 GET 본문 없음, status는 응답 본문 대신 HTTP 코드만 출력하는 선택값입니다.
for i in {1..20}; do
  lab_api GET /api/hello '' status
done
```

429가 관찰되지 않으면 요청 루프가 느렸는지, 설정한 replenish/burst가 무엇인지
확인합니다. 학습용 작은 burst 실험으로 충분하며 무제한 부하를 발생시키지 않습니다.
버킷이 회복된 뒤 다음 요청을 보내면 400이 기대됩니다.

```bash
# [대기] 앞선 burst 실험의 버킷 회복을 위해 3초 대기. 다른 동시 요청이 있으면 결과가 달라질 수 있습니다.
sleep 3
# [실패 확인] 빈 name/음수 value로 DTO 검증을 의도적으로 실패시킵니다. 인증/제한 통과 후 400을 예상합니다.
lab_api POST /api/echo '{"name":"","value":-1}' status
```

이 실습의 401/400/429는 서로 다른 계층에서 발생하지만 공통 Problem Details 형식으로 반환합니다.
401은 Gateway Security, 잘못된 echo DTO의 400은 Backend 검증, 429는 Gateway의
`RequestRateLimiter` → `throw-on-limit` → Advice/전역 handler의 `GatewayErrorResponses` 경로입니다.
429가 발생하면 Backend에는 요청을 보내지 않습니다. [오류 처리 범위](../api-contract.md#시스템별-오류-처리-경계)를 참고합니다.

## 체크 / 종료

- [ ] 실제 비밀값이 소스·콘솔·문서에 남지 않았다.
- [ ] 인증 누락 401, 정상 200, DTO 검증 400, 과다 요청 429를 구분했다.
- [ ] 클라이언트가 전달한 `X-Gateway-User`가 신뢰 근거가 아님을 이해했다.
- [ ] local 데모 발급기와 운영 OIDC Provider를 구분했다.
- [ ] 다음 관측성 단계까지는 Compose를 유지한다.

토큰 사용을 마쳤으면 `unset LAB_TOKEN LAB_BASE_URL`로 셸 메모리에서 제거합니다.

중단할 때는 `docker compose stop`, 재개할 때는 `docker compose start`입니다.
제거는 `docker compose down`이며 다른 프로젝트를 대상으로 하는 전역 prune은 사용하지 않습니다.

참고: [Spring Cloud Gateway RequestRateLimiter](https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway-server-webflux/gatewayfilter-factories/requestratelimiter-factory.html)

## 구조·코드 이해 체크

실행 결과를 확인한 뒤 아래 항목을 관련 파일과 연결해 설명합니다. 모든 클래스를 암기하기보다 요청 한 건과 설정 한 개를 끝까지 추적하세요.

**읽을 파일:** [Compose](../../docker-compose.yml) · [Gateway Dockerfile](../../gateway/Dockerfile) · [Backend Dockerfile](../../backend/Dockerfile) · [Gateway 설정](../../gateway/src/main/resources/application.yml) · [패키지 책임](../package-structure.md) · [응답 계약](../api-contract.md)

- [ ] Client → Gateway:8080 → Backend:8081 경로와 Gateway → Redis:6379 요청 제한 경로를 그릴 수 있다.
- [ ] `ports`의 호스트 공개와 `expose`의 내부 포트 표시를 구분하고, 컨테이너 간 주소가 `localhost` 대신 서비스 이름인 이유를 설명한다.
- [ ] Dockerfile의 Gradle 빌드 단계와 JRE 실행 단계를 구분하고, `bootJar` 성공과 테스트 통과가 다른 확인임을 설명한다.
- [ ] 설정 하나가 `.env` → Compose 환경변수 → `application.yml`의 `${...}` 참조 → `@ConfigurationProperties`가 붙은 `SecurityProperties` 객체로 바인딩되어 사용되는 과정을 추적했다.
- [ ] `AuthController → TokenService`의 데모 JWT 발급과 `SecurityConfig/JwtConfig`의 검증을 구분하고, issuer·audience·만료·scope 역할을 설명한다.
- [ ] `RequestHeadersFilter`가 인증 Principal로 사용자 헤더를 덮어쓰며 Backend는 헤더 대신 재검증한 JWT Principal을 사용하는 이유를 설명한다.
- [ ] `principalKeyResolver → RequestRateLimiter → StripPrefix=1 → Backend Controller → Service → DTO`를 실제 파일에서 찾았다.
- [ ] 200·401·400·429가 발생한 위치를 구분한다. echo 실습의 400은 인증/제한을 통과한 뒤 Backend DTO 검증에서, 429는 Gateway 요청 제한에서 Backend 도달 전에 발생한다.
- [ ] readiness에 Redis가 포함되는 이유와 liveness에서 외부 의존성을 제외한 이유를 설명한다.
- [ ] `RequestIdWebFilter/RequestIdFilter`의 요청 ID를 양쪽 로그 및 응답과 연결하고, 요청 ID가 인증이나 분산 trace 자체는 아님을 설명한다.

### 설정 추적 예: JWT_TTL

다른 설정 소스에서 같은 값을 덮어쓰지 않는 기본 로컬 구성을 기준으로 추적합니다.

| 단계 | 실제 파일과 처리 |
|---|---|
| 값 정의 | [생성 스크립트](../../scripts/new-local-env.sh)가 `.env`에 `JWT_TTL=1h`를 기록 |
| 컨테이너에 전달 | [Compose](../../docker-compose.yml)의 `environment`에서 `JWT_TTL: ${JWT_TTL:-1h}`를 해석해 컨테이너 환경변수로 전달 |
| Spring에서 값 해석 | [Gateway application.yml](../../gateway/src/main/resources/application.yml)의 `app.security.jwt.ttl: ${JWT_TTL:1h}`를 Spring `Environment`의 값으로 해석 |
| 객체에 바인딩 | [SecurityProperties](../../gateway/src/main/java/com/example/gateway/config/properties/SecurityProperties.java)의 `@ConfigurationProperties(prefix = "app.security")`에 따라 `jwt.ttl`을 `Duration` 타입으로 변환 |
| 등록과 사용 | [SecurityConfig](../../gateway/src/main/java/com/example/gateway/config/security/SecurityConfig.java)의 `@EnableConfigurationProperties`로 설정 Bean을 등록하고, [TokenService](../../gateway/src/main/java/com/example/gateway/auth/service/TokenService.java)가 `properties.jwt().ttl().toSeconds()`로 3600초를 얻어 토큰 만료 시각 계산 |

`@ConfigurationProperties`는 애너테이션이고 `SecurityProperties`는 실제 설정 객체의 타입입니다.
Spring이 `.env`를 직접 읽거나 `application.yml` 파일 내용을 수정하는 것은 아닙니다.
Compose가 `.env` 등을 이용해 변수를 치환하고, Spring은 전달받은 환경변수와 설정 파일을
`Environment`에서 조회해 값을 해석합니다. 같은 이름의 셸 환경변수가 있으면 Compose에서 `.env`보다 우선합니다.
`${JWT_TTL:-1h}`는 Compose 문법, `${JWT_TTL:1h}`는 Spring 문법이며 둘 다 이 예에서는 기본값을 지정합니다.
바인딩과 검증의 역할 구분은 [설정 객체와 애너테이션](../package-structure.md#설정-객체와-애너테이션)을 참고합니다.

참고: [Compose 변수 치환](https://docs.docker.com/compose/how-tos/environment-variables/variable-interpolation/),
[Spring 외부화 설정](https://docs.spring.io/spring-boot/reference/features/external-config.html)

**학습 기록:** 예상 경로 → 관찰한 HTTP 코드·로그·지표 → 근거 파일 → 복구 결과(해당 시) → 아직 설명하지 못하는 부분을 적습니다. 비밀번호·JWT·Secret 값은 적지 않습니다.

**다음 학습:** 위 요청 흐름을 설명할 수 있으면 [2단계](02-observability.md)로 진행합니다. Security/Filter의 상세 API는 이후 다시 읽어도 됩니다.
