# 2단계: Prometheus와 Grafana로 동작 관찰

[전체 순서](README.md) · 이전: [Compose](01-setup-and-compose.md) · 다음: [kind](03-kind-kubernetes.md)

## 목표 / 구현 위치

로그와 metrics를 연결해 정상 요청·오류·지연을 관찰합니다.
기존 `docker-compose.observability.yml`, `observability/prometheus.yml`,
`observability/grafana/provisioning/datasources/prometheus.yml`을 사용합니다.
이 단계는 **Compose 관측**입니다. kind에 Prometheus Operator를 설치하는 단계가 아닙니다.

## 기동

WSL Bash의 저장소 루트에서 실행합니다. Windows 브라우저에서 관측 화면을 열 수 있습니다.
연결이 안 되면 [WSL 네트워크 진단](07-troubleshooting-and-cleanup.md)을 먼저 확인합니다.

```bash
bash scripts/ensure-observability-env.sh
docker compose -f docker-compose.yml -f docker-compose.observability.yml \
  --profile observability config --quiet
docker compose -f docker-compose.yml -f docker-compose.observability.yml \
  --profile observability up -d --build
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
3. 아래 PromQL을 순서대로 조회합니다. scrape 주기 15초, rate window 5분이므로
   바로 빈 결과가 나오면 샘플이 쌓인 뒤 확인합니다.
4. Grafana Explore에서 이미 provision된 `Prometheus` datasource를 선택합니다.
5. `Local Gateway Lab` dashboard를 만들고 같은 쿼리의 패널을 추가·저장합니다.

```promql
up{job=~"gateway|backend"}
```

```promql
sum by (job, status) (rate(http_server_requests_seconds_count[5m]))
```

```promql
sum by (job) (rate(http_server_requests_seconds_sum[5m]))
/
sum by (job) (rate(http_server_requests_seconds_count[5m]))
```

마지막 쿼리는 평균 응답 시간(초)입니다. 요청량이 0이면 NaN이 나올 수 있습니다.
두 앱의 `http.server.requests` histogram과 SLO bucket을 활성화했습니다.
다음은 서비스별 p95(초)입니다. 저트래픽/샘플 부족에서는 NaN이나 불안정한 값이 나옵니다.

```promql
histogram_quantile(0.95,
  sum by (job, le) (rate(http_server_requests_seconds_bucket[5m])))
```

건강 검사까지 합산되는 쿼리이므로 API만 보려면 실제 `uri` label을 확인하고 필터를 추가합니다.
Gateway route별 요청은 별도 지표로 관찰합니다.

```promql
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
docker compose -f docker-compose.yml -f docker-compose.observability.yml \
  --profile observability down
ss -ltn 'sport = :8080'
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
