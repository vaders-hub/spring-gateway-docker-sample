# 02단계 별첨: Micrometer와 Actuator의 지표 수집 원리

[02단계로 돌아가기](02-observability.md) · [학습 목차](README.md) · [시스템 구성도](../system-architecture.md)

작성 기준: 2026-09-23, 현재 저장소의 Compose 관측 설정.
이 문서는 구성 원리를 설명하며 별도 기동이나 설정 변경을 요구하지 않습니다.

## 1. 네 구성요소의 역할

**Micrometer는 각 앱 내부에서 지표를 측정하고, Actuator는 지표를 조회하는 HTTP 창구를 제공합니다.
Prometheus는 그 창구를 주기적으로 호출해 값을 저장하고, Grafana는 저장된 값을 조회해 시각화합니다.**

| 구성요소 | 역할 | 현재 실행 위치 |
|---|---|---|
| Micrometer | 요청 횟수·처리 시간·JVM 메모리 등 지표 측정 | Gateway와 Backend 각각의 JVM 내부 |
| Spring Boot Actuator | 상태·지표 등을 HTTP 엔드포인트로 제공 | Gateway와 Backend 각각의 앱 내부 |
| Prometheus | 지표를 수집하고 시간별 값으로 저장 | 별도 Prometheus 컨테이너 |
| Grafana | PromQL 조회 결과를 패널·대시보드로 표시 | 별도 Grafana 컨테이너 |

Micrometer나 Actuator를 위해 별도 컨테이너를 띄우지 않습니다. 각 앱의 라이브러리로 포함됩니다.

## 2. Micrometer는 무엇을 측정하나요?

Micrometer는 JVM 애플리케이션에서 공통 API로 지표를 기록할 수 있도록 하는 라이브러리입니다.
Prometheus에 종속된 업무 측정 코드를 작성하지 않고도 여러 모니터링 시스템과 연결할 수 있습니다.

| 도구 | 측정 대상 | 예시 |
|---|---|---|
| `Counter` | 누적 발생 횟수 | 주문 완료 건수, 결제 실패 건수 |
| `Timer` | 처리 횟수와 소요 시간 | HTTP 요청 처리 시간 |
| `Gauge` | 측정 시점의 값 | 현재 사용 중인 JVM 메모리, 현재 대기 작업 수 |

이 측정 도구들을 등록·관리하는 객체가 `MeterRegistry`입니다.
현재는 Prometheus용 registry를 통해 지표를 Prometheus가 읽을 수 있는 형식으로 제공합니다.

HTTP 요청은 Spring Boot의 자동 계측이 처리하므로 Controller마다 직접 시간을 재는 코드를
작성하지 않아도 됩니다. JVM·프로세스 등 기본 지표도 자동 구성으로 등록됩니다.
반면 “결제 실패 건수”처럼 업무 의미가 필요한 지표는 개발자가 Micrometer를 사용하는 코드를 추가해야 합니다.

HTTP 지표 `http.server.requests`는 Timer로 기록됩니다. Prometheus에서 보이는 다음 지표는
서로 무관한 세 개의 업무 카운터가 아니라 이 측정 결과를 표현한 것입니다.

| Prometheus 지표 | 의미 |
|---|---|
| `http_server_requests_seconds_count` | 누적 요청 처리 횟수 |
| `http_server_requests_seconds_sum` | 누적 처리 시간, 단위는 초 |
| `http_server_requests_seconds_bucket` | 시간 구간별 누적 처리 횟수, histogram/SLO 설정 시 제공 |

`method`, `uri`, `status` 같은 라벨로 요청 종류를 구분합니다.
각 앱/프로세스는 자기 지표를 관리하므로 Gateway와 Backend의 요청 수가 하나의 공통 카운터에 기록되는 것은 아닙니다.

## 3. /actuator/prometheus는 누가 호출하고 응답하나요?

```text
Prometheus 컨테이너 (15초마다 GET)
   │
   ├─ http://gateway:8080/actuator/prometheus
   │      └─ Gateway의 Netty
   │            └─ Gateway에 내장된 Actuator
   │                  └─ Gateway의 Micrometer registry 지표 반환
   │
   └─ http://backend:8081/actuator/prometheus
          └─ Backend의 Tomcat
                └─ Backend에 내장된 Actuator
                      └─ Backend의 Micrometer registry 지표 반환
```

**Backend 지표 수집은 Gateway를 거치지 않고 Backend에 직접 요청합니다.**
동일한 `/actuator/prometheus` 경로이지만 접속하는 서비스·포트가 다르므로 각각 자기 앱의 지표를 응답합니다.
이 경로는 직접 작성한 업무 Controller가 아니라 Spring Boot가 자동 구성한 Actuator 엔드포인트입니다.
응답은 일반 업무 API의 `data/meta` JSON이 아니라 Prometheus가 읽는 지표 형식입니다.

Grafana의 패널 조회는 Prometheus에 보내는 요청입니다. Grafana가 두 앱의 Actuator를 직접 수집하는 것은 아닙니다.

## 4. 프로젝트 코드와 설정에서 연결하기

### 앱에 라이브러리 포함

[Gateway build.gradle](../../gateway/build.gradle)과 [Backend build.gradle](../../backend/build.gradle)에
모두 다음 의존성이 있습니다.

```groovy
// 운영 상태·지표 엔드포인트와 자동 계측 지원
implementation 'org.springframework.boot:spring-boot-starter-actuator'

// Micrometer 지표를 Prometheus 형식으로 제공
runtimeOnly 'io.micrometer:micrometer-registry-prometheus'
```

### HTTP 엔드포인트 노출

[Gateway application.yml](../../gateway/src/main/resources/application.yml)과
[Backend application.yml](../../backend/src/main/resources/application.yml)의 설정입니다.

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus
```

엔드포인트를 노출하는 설정과 익명 접근을 허용하는 설정은 별개입니다.
현재 [관측 Compose](../../docker-compose.observability.yml)는 두 앱에 `PROMETHEUS_PUBLIC: "true"`를 전달하고,
각 앱의 SecurityConfig가 이를 읽어 로컬 학습용 익명 수집을 허용합니다.
기본값은 `false`이며 현재의 무인증 수집 설정을 운영 환경에 그대로 적용하는 구조는 아닙니다.

### Prometheus의 수집 대상 등록

[observability/prometheus.yml](../../observability/prometheus.yml)에 호출 주기와 대상이 있습니다.

```yaml
global:
  scrape_interval: 15s

scrape_configs:
  - job_name: gateway
    metrics_path: /actuator/prometheus
    static_configs:
      - targets: ["gateway:8080"]

  - job_name: backend
    metrics_path: /actuator/prometheus
    static_configs:
      - targets: ["backend:8081"]
```

`gateway`, `backend`는 같은 Compose 네트워크 내부에서 사용하는 서비스 이름입니다.
Windows 브라우저에서 `http://backend:8081`로 접속할 수 없는 것과 Prometheus의 수집 성공은 모순이 아닙니다.
Backend 포트는 현재 호스트에 공개하지 않았습니다.

## 5. 요청마다 저장되나요, 15초마다 저장되나요?

앱은 요청이 처리될 때 지표를 갱신하고, Prometheus는 15초마다 그때의 지표 값을 가져옵니다.
요청·응답 본문, JWT, 개별 요청 ID를 HTTP 지표의 내용으로 저장하는 방식은 아닙니다.

다음은 설명용 가상 예시입니다. 실제 측정 결과가 아닙니다.

| 수집 시각 | 특정 라벨 조합의 누적 요청 수 |
|---|---:|
| 10:00:00 | 100 |
| 10:00:15 | 130 |
| 10:00:30 | 175 |

지표 이름과 라벨 조합이 시계열을 구분하고, 각 시계열에 시각·숫자 샘플이 쌓입니다.
Prometheus는 현재 `/prometheus` 저장 경로에 연결된 `prometheus-data` named volume을 사용합니다.
Grafana의 `grafana-data` volume은 대시보드·설정 등을 보관하는 별도의 저장소입니다.

앱 프로세스가 재시작되면 해당 프로세스의 누적 카운터는 초기화될 수 있습니다.
Prometheus에 이미 저장된 이전 샘플은 별개로 유지되며 `rate()`는 카운터 초기화를 고려해 증가율을 계산합니다.
Gauge는 순간값이므로 수집 사이에 잠깐 발생했다 사라진 최고값까지 항상 포착하는 것은 아닙니다.

```promql
rate(http_server_requests_seconds_count[5m])
```

위 식은 최근 5분의 누적 카운터 샘플을 사용해 초당 평균 요청 수를 계산합니다.
`[5m]`은 조회에 사용하는 시간 구간이고 `15s`는 수집 주기입니다.
Gateway와 Backend가 같은 업무 요청을 각각 측정하므로 양쪽 카운터를 단순 합산한 값을
고유 사용자 요청 수라고 해석하지 않습니다.

## 6. 이해 확인

- [ ] Micrometer·Actuator는 각 앱 내부 라이브러리이고 Prometheus·Grafana는 별도 서비스임을 설명한다.
- [ ] Prometheus가 두 앱의 `/actuator/prometheus`를 각각 직접 호출하는 경로를 설명한다.
- [ ] 지표 측정, HTTP 노출, 인증 허용, 수집 대상 등록을 실제 파일에서 찾았다.
- [ ] 앱의 누적 지표와 Prometheus에 저장된 시간별 샘플을 구분한다.
- [ ] `up=1`은 수집 성공이며 전체 업무 API의 정상 동작을 보장하지 않는다는 점을 설명한다.
- [ ] 전체 요청량·지연 분석은 지표로, 특정 요청의 원인 추적은 로그의 `requestId`로 확인한다.

## 공식 참고 자료

- [Micrometer 측정 도구](https://docs.micrometer.io/micrometer/reference/concepts/meters.html)
- [Micrometer의 Prometheus 연동](https://docs.micrometer.io/micrometer/reference/implementations/prometheus.html)
- [Spring Boot Actuator 지표](https://docs.spring.io/spring-boot/reference/actuator/metrics.html)
- [Prometheus 쿼리 기본](https://prometheus.io/docs/prometheus/latest/querying/basics/)
- [Prometheus rate 함수](https://prometheus.io/docs/prometheus/latest/querying/functions/#rate)
