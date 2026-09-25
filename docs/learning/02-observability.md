# 2단계: Prometheus와 Grafana로 동작 관찰

[전체 순서](README.md) · 이전: [Compose](01-setup-and-compose.md) · 다음: [kind](03-kind-kubernetes.md)

별첨: [Micrometer와 Actuator의 지표 수집 원리](02-appendix-micrometer-actuator.md) — 각 앱의 측정·응답과 Prometheus의 호출·저장 경로를 설명합니다.

## 목표 / 구현 위치

로그와 metrics를 연결해 정상 요청·오류·지연을 관찰합니다.
기존 `docker-compose.observability.yml`, `observability/prometheus.yml`,
`observability/grafana/provisioning/datasources/prometheus.yml`을 사용합니다.
이 단계는 **Compose 관측**입니다. kind에 Prometheus Operator를 설치하는 단계가 아닙니다.

## 기동

WSL Bash의 저장소 루트에서 실행합니다. Windows 브라우저에서 관측 화면을 열 수 있습니다.
연결이 안 되면 [WSL 네트워크 진단](07-troubleshooting-and-cleanup.md)을 먼저 확인합니다.

```bash
# [준비/파일 변경] 기존 .env에 Grafana 비밀번호 키가 없을 때만 랜덤 값을 추가. 기존 자격증명은 보존합니다.
bash scripts/ensure-observability-env.sh
# [검증] -f 두 개로 기본 앱과 관측 설정을 합치고, observability profile의 서비스를 포함해 검사합니다.
# --quiet는 비밀값이 포함된 최종 설정 출력을 막습니다. 컨테이너를 실행하는 단계는 아닙니다.
docker compose -f docker-compose.yml -f docker-compose.observability.yml \
  --profile observability config --quiet
# [기동] 앱과 Prometheus/Grafana를 백그라운드 실행. --build는 앱 이미지 빌드도 수행합니다.
# 관측용 환경변수 변경으로 기존 앱 컨테이너가 재생성될 수 있습니다. 데이터 volume은 유지됩니다.
docker compose -f docker-compose.yml -f docker-compose.observability.yml \
  --profile observability up -d --build
# [조회] 같은 파일/profile 조합으로 서비스 상태 확인. 앱 healthy와 관측 서비스 실행 상태를 봅니다.
docker compose -f docker-compose.yml -f docker-compose.observability.yml \
  --profile observability ps
```

Prometheus는 `http://localhost:9090`, Grafana는 `http://localhost:3000`입니다.
Grafana 계정은 `admin`, 비밀번호는 로컬 `.env`의 `GRAFANA_ADMIN_PASSWORD`입니다.
로컬 편집기로 확인하고 화면 공유/저장소에 노출하지 않습니다. 기존 Grafana volume이
있으면 `.env` 비밀번호 변경만으로 기존 관리자 비밀번호가 바뀌지는 않을 수 있습니다.

## 구현 실습: 첫 dashboard

1. 1단계의 JWT/API 호출을 실행해 요청 지표를 만듭니다.
2. Prometheus Targets 화면에서 gateway/backend가 모두 UP인지 확인합니다.
3. [Prometheus Query](http://localhost:9090/query)의 입력창에 아래 PromQL을 하나씩 붙여 넣고 `Execute`를 누릅니다. scrape 주기 15초, rate window 5분이므로
   바로 빈 결과가 나오면 샘플이 쌓인 뒤 확인합니다.
4. Grafana Explore에서 이미 provision된 `Prometheus` datasource를 선택합니다.
5. `Local Gateway Lab` dashboard를 만들고 같은 쿼리의 패널을 추가·저장합니다.

패널 편집 화면의 `Queries → Data source`에서 `Prometheus`를 선택하고 `Code` 모드에
PromQL을 입력한 뒤 `Run queries`를 누릅니다. `-- Grafana --`의 `Random Walk`는 샘플 데이터이며,
`Expression`의 SQL 입력창은 PromQL을 실행하는 곳이 아닙니다. `=~` 앞에 역슬래시를 붙이지 않습니다.

### Prometheus 데이터 소스가 목록에 없을 때

이 프로젝트는 시작 시 Prometheus 데이터 소스를 자동 등록합니다. 등록 로그가 있어도
플러그인이 로딩되지 않으면 선택 목록에 보이지 않을 수 있습니다.
Grafana 13.2.2에서 기본 플러그인을 시작 시 자동 업데이트하다가 읽기 전용 이미지 경로에
쓰지 못하면 `Failed to install plugin` / `read-only file system` 이후
`Could not find plugin definition for data source`가 발생할 수 있습니다.

관측 Compose의 `GF_PLUGINS_PREINSTALL_AUTO_UPDATE: "false"`는 시작 시 자동 업데이트를 끄고
이미지에 포함된 플러그인을 사용하도록 합니다. `read_only: true`와 기존 데이터 volume은 유지합니다.
플러그인 버전 갱신은 Grafana 이미지 업그레이드 시 별도로 검증합니다.

```bash
# [설정 반영] grafana만 시작/변경 시 재생성. --no-deps는 의존 서비스까지 함께 시작하지 않게 합니다.
# 이미 같은 설정으로 실행 중이면 재생성하지 않을 수 있습니다. 기존 데이터 volume은 지우지 않습니다.
docker compose -f docker-compose.yml -f docker-compose.observability.yml \
  --profile observability up -d --no-deps grafana
# [조회] 최근 2분 Grafana 로그에서 플러그인/데이터 소스 오류가 남아 있는지 확인합니다.
docker compose -f docker-compose.yml -f docker-compose.observability.yml \
  --profile observability logs --since 2m grafana
```

Grafana가 다시 기동되면 브라우저를 새로고침하고 데이터 소스를 선택합니다.
`down -v`나 데이터 소스 중복 생성은 필요하지 않습니다.
[자동 업데이트 설정](https://grafana.com/docs/grafana/latest/setup-grafana/configure-grafana/#preinstall_auto_update)을 참고합니다.

### 조회할 PromQL

아래는 **Bash 명령이 아니라 Prometheus Query 또는 Grafana의 PromQL Code 입력창에서 실행하는 조회식**입니다.
`#` 줄은 PromQL 설명 주석이므로 함께 붙여 넣어도 됩니다. 각 코드 블록을 하나씩 실행합니다.
쿼리는 저장된 지표를 조회할 뿐 앱 요청을 생성하거나 서버 설정을 바꾸지 않습니다.

```promql
# [수집 상태] 두 job만 선택. =~는 정규식 비교, |는 둘 중 하나를 뜻합니다.
# 값 1은 최근 scrape 성공, 0은 실패. 업무 API 전체의 정상 여부를 보장하지 않습니다.
up{job=~"gateway|backend"}
```

```promql
# [요청률] 최근 5분 카운터 증가를 초당 평균 요청 수로 계산하고 앱(job)·상태 코드별로 합칩니다.
# 누적 총건수가 아니라 requests/second입니다. Gateway와 Backend는 별도로 해석합니다.
sum by (job, status) (rate(http_server_requests_seconds_count[5m]))
```

```promql
# [평균 지연/초] 앱별 처리 시간 증가율을 요청 수 증가율로 나눕니다.
# 아래 세 줄이 하나의 식입니다. 요청 수가 0이면 NaN이 나올 수 있습니다.
sum by (job) (rate(http_server_requests_seconds_sum[5m]))
/
sum by (job) (rate(http_server_requests_seconds_count[5m]))
```

마지막 쿼리는 평균 응답 시간(초)입니다. 요청량이 0이면 NaN이 나올 수 있습니다.
두 앱의 `http.server.requests` histogram과 SLO bucket을 활성화했습니다.
다음은 서비스별 p95(초)입니다. 저트래픽/샘플 부족에서는 NaN이나 불안정한 값이 나옵니다.

```promql
# [p95 지연/초] 최근 5분 histogram으로 요청의 약 95%가 이 시간 안에 처리되는 경계를 추정합니다.
# le는 누적 bucket의 상한 label이므로 합산 때 유지합니다. 개별 요청의 정확한 최대 시간이 아닙니다.
histogram_quantile(0.95,
  sum by (job, le) (rate(http_server_requests_seconds_bucket[5m])))
```

건강 검사까지 합산되는 쿼리이므로 API만 보려면 실제 `uri` label을 확인하고 필터를 추가합니다.
Gateway route별 요청은 별도 지표로 관찰합니다.

```promql
# [라우트 요청률] SCG routeId·상태별 초당 평균 요청 수. 라우트 필터에 도달한 요청 범위를 봅니다.
# 앞단 Security에서 거절된 401 등은 빠질 수 있으므로 HTTP 서버 지표와 함께 확인합니다.
sum by (routeId, status) (rate(spring_cloud_gateway_requests_seconds_count[5m]))
```

Gateway metrics는 기존 버전에서도 기본 활성화되지만 설정에 명시했습니다.
Security에서 거절한 요청은 route filter까지 도달하지 않아 route 지표에 없을 수 있습니다.
401은 Gateway HTTP 지표, 정상 요청은 Gateway/Backend HTTP와 route 지표를 함께 비교하세요.
실제 label 이름과 metric 노출은 실행 후 확인해야 합니다.

앱 식별자는 `application=gateway|backend`, 환경은 `environment=local`처럼 분리합니다.
histogram은 시계열 수를 늘립니다. 범위는 1ms~5s, SLO는 100/250/500ms·1/2s이고,
`HTTP_HISTOGRAM_ENABLED=false`는 percentile histogram을 끕니다(SLO bucket은 별도 설정).
raw URL 기반 Gateway path tag는 비활성화했습니다.
지표가 없으면 Prometheus의 metric 목록과 앱 `/actuator/prometheus`에서 실제 이름을 확인합니다.
Grafana datasource URL은 컨테이너 내부의 `http://prometheus:9090`이며 `localhost`가 아닙니다.

## 로그와 보안 확인

```bash
# [로그 조회] 최근 5분의 Gateway/Backend 로그에서 실습 requestId가 포함된 줄만 골라 봅니다.
# |는 출력을 다음 명령으로 전달, grep -F는 정규식이 아닌 고정 문자열 검색입니다.
# 결과가 없으면 호출 시각/요청 ID/로그 설정을 확인합니다. 로그를 생성하거나 변경하는 명령은 아닙니다.
docker compose logs --since 5m gateway backend | grep -F 'local-learning-001'
```

1단계에서 사용한 requestId가 양쪽 로그에 나타나는지 확인합니다. requestId와 traceId는
같은 개념이 아닙니다. 현재 OTel 계측 설정은 있지만 Collector/trace 저장소가 없고 export도
꺼져 있으므로 Grafana에서 분산 trace까지 보인다고 기대하지 않습니다.

관측 overlay는 local 환경에 한해 Prometheus 익명 scrape를 허용합니다.
이 설정을 prod에 복사하지 않습니다. kind 기본 구성은 Prometheus 인증이 필요합니다.

## 체크 / kind로 넘어가기

- [ ] gateway/backend의 `up`이 각각 1이다.
- [ ] 요청을 발생시킨 뒤 상태 코드별 rate, 평균 latency, p95를 확인했다.
- [ ] Gateway route 지표와 HTTP 서버 지표의 수집 범위 차이를 확인했다.
- [ ] backend에 도달하기 전 거절된 요청은 gateway와 backend 지표가 다를 수 있음을 이해했다.
- [ ] logs/metrics/traces의 차이를 설명할 수 있다.
- [ ] dashboard는 직접 만든 결과이며 저장소에 완성본이 기본 포함된 것은 아니다.

```bash
# [제거] 이 프로젝트의 앱/관측 컨테이너와 네트워크를 내려 kind용 8080을 비웁니다.
# -v를 붙이지 않으므로 Prometheus/Grafana named volume은 보존합니다.
docker compose -f docker-compose.yml -f docker-compose.observability.yml \
  --profile observability down
# [조회] WSL에서 8080 TCP 포트의 수신 대기가 남았는지 확인. Windows 쪽 점유는 별도 확인입니다.
ss -ltn 'sport = :8080'
# [조회] 남은 Docker 컨테이너의 포트 매핑 확인. 무관한 컨테이너를 임의로 종료하지 않습니다.
docker ps --format 'table {{.Names}}\t{{.Ports}}'
```

kind를 생성하기 전에 8080을 비웁니다. 위 `down`은 volume을 지우지 않아 Grafana/Prometheus
데이터를 보존합니다. `-v`를 붙이면 저장 데이터도 삭제되므로 이 과정에서는 붙이지 않습니다.
관측 기능을 사용한 적이 있다면 기본 compose 파일만으로 내리지 말고 두 파일을 함께 지정합니다.

공식 근거: [PromQL](https://prometheus.io/docs/prometheus/latest/querying/basics/),
[Grafana datasource](https://grafana.com/docs/grafana/latest/datasources/prometheus/),
[Spring Boot metrics](https://docs.spring.io/spring-boot/reference/actuator/metrics.html),
[Gateway metrics filter](https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway-server-webflux/global-filters.html)

## 구조·코드 이해 체크

실행 결과를 확인한 뒤 아래 항목을 관련 파일과 연결해 설명합니다. 모든 클래스를 암기하기보다 요청 한 건과 설정 한 개를 끝까지 추적하세요.

**읽을 파일:** [관측 Compose](../../docker-compose.observability.yml) · [scrape 설정](../../observability/prometheus.yml) · [Grafana datasource](../../observability/grafana/provisioning/datasources/prometheus.yml) · [Gateway 계측](../../gateway/src/main/resources/application.yml) · [Backend 계측](../../backend/src/main/resources/application.yml)

- [ ] 앱의 `/actuator/prometheus` → Prometheus 수집 → Grafana 조회 경로를 설명하고, Grafana가 앱에 직접 지표를 요청하는 구조가 아님을 이해했다.
- [ ] Compose overlay가 기본 서비스 환경변수를 합치는 방식과 `observability` profile로 추가되는 서비스를 찾았다.
- [ ] `up=1`은 scrape 성공이며 업무 API 정상 동작을 모두 보장하지 않는다는 점을 설명한다.
- [ ] 정상 요청과 Gateway에서 거절한 401 요청을 비교해 Gateway/Backend HTTP 지표 및 route 지표의 차이를 설명한다.
- [ ] counter 증가량, rate, 평균 지연, histogram 기반 p95를 구분하고 샘플 부족/무요청 시 결과를 해석한다.
- [ ] 건강 검사도 HTTP 지표에 포함됨을 확인하고 API 분석 시 실제 `uri` label을 보고 필터링한다.
- [ ] requestId로 로그를 연결하고 logs/metrics/traces 역할을 구분한다. 현재 trace 저장소와 완성 dashboard는 기본 제공되지 않는다.
- [ ] 익명 scrape 허용은 local 학습 설정임을 이해하고, named volume과 `down`/`down -v`의 데이터 보존 차이를 설명한다.

**학습 기록:** 예상 경로 → 관찰한 HTTP 코드·로그·지표 → 근거 파일 → 복구 결과(해당 시) → 아직 설명하지 못하는 부분을 적습니다. 비밀번호·JWT·Secret 값은 적지 않습니다.

**다음 학습:** 관측 Compose를 종료해 8080을 비운 뒤 [3단계](03-kind-kubernetes.md)로 넘어갑니다.
