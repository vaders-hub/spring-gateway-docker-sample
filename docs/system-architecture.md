# 학습 시스템 구성도

갱신: 2026-09-25. **03단계 완료라는 사용자 진행 보고와 저장소 설정**을 기준으로 작성한 구성도입니다.
이번 갱신에서 실행 중인 클러스터를 직접 점검하거나 모든 테스트의 통과 여부를 확인한 것은 아닙니다.
상단은 03단계 kind/Kubernetes 구성, 하단은 이전 01–02단계 Compose 관측 구성과 이후 학습 범위입니다.

[SVG 원본 열기](diagrams/learning-system-overview.svg) · [고해상도 PNG 열기](diagrams/learning-system-overview.png)

![학습 시스템 구성도](diagrams/learning-system-overview.png)

SVG는 확대해도 선명한 벡터 파일이며 브라우저나 SVG 편집기에서 열 수 있습니다.
PNG는 3600 × 3500 크기로 문서·발표 자료에 삽입할 수 있습니다. 구성이 바뀌면 두 파일을 함께 갱신합니다.

## 현재 03단계 구성 읽기

화살표는 호출·수집 요청 방향입니다. 응답·조회 결과는 반대 방향으로 돌아오며 선의 교차는 연결 지점이 아닙니다.

1. 클라이언트는 `localhost:8080`으로 접근합니다. kind의 포트 매핑이 요청을 노드의 `30080` 포트로 전달하고, Gateway NodePort Service가 준비된 Gateway Pod로 연결합니다.
2. Gateway의 `POST /auth/token`으로 로컬 데모 JWT를 발급받습니다.
3. `Authorization: Bearer ...`를 포함해 `/api/**`를 호출합니다. Gateway는 JWT·scope·CORS와 요청 ID를 처리합니다.
4. Gateway가 `redis:6379` Service를 통해 사용자별 제한 버킷을 확인합니다. 허용하면 `/api`를 제거해 `backend:8081` Service로 전달하고, 한도 초과면 Backend까지 가지 않고 429로 끝납니다.
5. Backend Pod는 JWT를 다시 검증하고 Controller → Service → DTO 흐름으로 요청을 처리합니다.

Gateway·Backend·Redis의 Deployment는 기본 복제본이 각각 1개입니다. 각 Pod의 컨테이너는 kind 노드 내부의
containerd가 실행합니다. Docker Desktop이 실행하는 **kind 노드 컨테이너**와 그 안의 **앱 컨테이너**는 서로 다른 계층입니다.
Gateway는 Netty, Backend는 내장 Tomcat에서 실행되며, 앱 이미지에 Java 실행 환경과 JAR가 포함됩니다.

| 이름 / 포트 | 의미 |
|---|---|
| `gateway-lab` (클러스터 이름) | kind 클러스터 생성 시 지정한 이름 |
| `kind-gateway-lab` (context) | kubectl이 해당 클러스터에 접속할 때 고르는 설정 이름 |
| `gateway-lab-control-plane` (노드 이름) | Docker Desktop에서 실행하는 단일 kind 노드 컨테이너 |
| `gateway-lab` (namespace) | Gateway·Backend·Redis 리소스를 묶는 Kubernetes 논리적 범위. 클러스터 이름과는 별개 |
| `localhost:8080` → 노드 `30080` | 호스트에서 kind로 들어가는 포트 매핑 |
| Gateway `8080:30080/TCP` | Service 포트 `8080` : 외부 진입용 NodePort `30080`. Gateway Pod의 앱 포트도 `8080` |
| Backend `backend:8081`, Redis `redis:6379` | 같은 namespace에서 사용하는 ClusterIP Service의 DNS 이름과 포트 |

Gateway만 NodePort인 이유는 이 단계에서 **호스트의 API 요청을 받는 진입점**이기 때문입니다.
Backend와 Redis는 클러스터 내부 통신용이므로 ClusterIP로 두고 호스트 포트를 공개하지 않습니다.
NodePort는 외부 IP를 자동으로 할당하지 않으므로 `EXTERNAL-IP`가 `<none>`이어도 정상입니다.
Service의 `10.96.x.x` 같은 IP는 재생성 시 달라질 수 있어 앱 설정에서는 DNS 이름을 사용합니다.

Namespace는 노드 안의 물리적 컨테이너나 방화벽이 아닙니다. 그림에서는 앱 리소스의 소속을 나타내려고
노드 영역 안에 표시했습니다. ClusterIP도 Gateway만 접근하도록 제한하는 보안 정책은 아닙니다.
NetworkPolicy 선언과 CNI의 실제 집행 여부는 구분하며, 접근 차단은 08단계 대조 실험으로 확인합니다.

설정은 `ConfigMap / Secret → Deployment 환경변수 → Spring Environment` 경로로 전달됩니다.
프로젝트 설정 일부는 `@ConfigurationProperties` 설정 객체에 바인딩됩니다.
환경변수로 주입한 설정을 변경한 경우 기존 Pod에 자동 반영되지 않으므로 04단계에서 rollout을 실습합니다.
자세한 설정 책임은 [패키지 구조](package-structure.md), 이름별 명령 예시는 [03단계](learning/03-kind-kubernetes.md)를 참고합니다.

Redis 장애의 기본 허용 fallback은 현재 유지합니다. 한도 초과 429와 Redis 오류 시 요청별 fail-closed는 별개이며,
후자는 [후속 장애 정책](resilience-policy.md)입니다. 공통 오류 형식과 401/403/429/502/504 처리 범위는
[API 응답 계약](api-contract.md)을 참고합니다.

## 이전 01–02단계 Compose 관측 구성

아래 구성은 이전 단계에서 사용한 별도 실행 방식이며 **03단계 kind에 자동으로 함께 배포되지 않습니다.**
Compose와 kind의 기본 API 진입 포트가 모두 8080이므로 전환 시 Compose를 종료합니다.
그림에 이전 구성을 남긴 것은 학습 내용을 비교하기 위한 것이며, 지금 두 구성이 동시에 실행 중이라는 뜻이 아닙니다.

| Compose 실행 시 Windows / WSL 호스트에서 접근 | 용도 |
|---|---|
| `http://localhost:8080` | Spring Gateway API 및 로컬 데모 토큰 발급 |
| `http://localhost:9090` | Prometheus Query / Targets UI |
| `http://localhost:3000` | Grafana UI |
| `backend:8081`, `redis:6379` | Compose 네트워크 내부 이름·포트. 호스트에 포트를 공개하지 않음 |

Prometheus는 두 앱의 `/actuator/prometheus`를 15초마다 가져와 저장합니다.
Grafana는 Prometheus에 PromQL을 보내 결과를 패널·대시보드로 표시합니다. 앱이 Grafana에 지표를 직접 보내지는 않습니다.
Compose의 `expose` 자체는 접근 제어 정책이 아닙니다. Grafana 데이터 소스 URL은 컨테이너 내부 주소
`http://prometheus:9090`이며 Windows 브라우저에서 사용하는 `localhost:9090`과 구분합니다.
Prometheus `up=1`은 지표 수집 성공을 의미하며 전체 업무 API의 정상 동작까지 보장하지 않습니다.

Prometheus와 Grafana는 각각 named volume을 사용합니다. Redis는 RDB/AOF를 끈 요청 제한 실습용이며 영속 DB가 아닙니다.
Compose 환경값은 `.env → Compose environment → Spring Environment`로 전달됩니다.
kind의 Redis도 이 단계에서는 영속 저장소를 사용하는 데이터베이스가 아닙니다.

## 후속 학습 구성 읽기

- **04 · 운영·복구**: 03단계에서 배포한 앱의 복제본, ConfigMap 변경, readiness, rollout과 복구를 실습합니다.
- **05 · 선택형 Envoy**: Gateway API 리소스로 Envoy Proxy를 설정하고 `localhost:8888` port-forward 경로를 추가합니다.
  기본 NodePort 경로도 남으며, 두 진입 경로 모두 Spring Gateway를 통과합니다. Envoy가 JWT·Redis 제한을 중복 수행하지 않습니다.
- **06 · AWS 비교 / 07 · 진단**: 로컬 구성을 AWS 구성 요소와 비교하고, 장애 상황의 확인 순서를 익힙니다. 실제 AWS 자원을 배포하지 않습니다.
- **08 · NetworkPolicy**: Backend/Redis로 들어오는 연결을 Gateway Pod에 허용하는 정책입니다. CNI의 실제 집행 여부는 별도 대조 실험으로 확인합니다.
- **09 · 저장소**: 별도 `gateway-storage-lab` namespace에서 BusyBox·PVC·PV로 Pod 교체와 백업·복원을 실습합니다.
  기존 Backend/Redis에 영속 저장소를 붙이는 단계가 아닙니다. StorageClass/local-path provisioner의 조건은 실행 전 확인합니다.
- **10 · Spring Boot**: Stateless 인증, 설정 외부화, health, stdout 로그, graceful shutdown과 미완료 장애 정책을 함께 검증합니다.

현재 학습 계획은 **08까지 진행한 뒤 AA 영역을 보강**하는 것입니다. 09–10은 이후 선택·보강 범위로 구분합니다.
Oracle/JPA/MyBatis, OTel Collector/trace 저장소, 실제 AWS 리소스는 현재 구성에 포함되지 않습니다.
kind는 EKS 에뮬레이터가 아니며 AWS 자원 생성은 이 무료 로컬 학습의 실행 범위 밖입니다.

## 구성도의 근거 파일

| 영역 | 소스 / 문서 |
|---|---|
| 이전 Compose 컨테이너·포트·환경변수 | [기본 Compose](../docker-compose.yml), [관측 Compose](../docker-compose.observability.yml) |
| 인증·요청 제한·Backend 라우팅 | [Gateway 설정](../gateway/src/main/resources/application.yml), [트래픽 진입 책임](traffic-entry.md) |
| 지표 수집·조회 | [Prometheus 설정](../observability/prometheus.yml), [Grafana 데이터 소스](../observability/grafana/provisioning/datasources/prometheus.yml) |
| 현재 kind / NodePort / 앱 배포 | [kind 설정](../k8s/kind-config.yaml), [기본 매니페스트](../k8s/base), [03단계](learning/03-kind-kubernetes.md) |
| Envoy / Gateway API | [선택 매니페스트](../k8s/gateway-api), [05단계](learning/05-gateway-api.md) |
| 정책·저장소·Spring Boot 검증 | [08단계](learning/08-network-policy-validation.md), [09단계](learning/09-storage-and-persistence.md), [10단계](learning/10-spring-boot-readiness.md) |

[전체 학습 순서](learning/README.md)
