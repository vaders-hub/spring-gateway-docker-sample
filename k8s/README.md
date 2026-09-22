# kind 배포

처음이라면 [무료 로컬 학습 순서](../docs/learning/README.md)부터 진행합니다.
아래 명령은 WSL Bash의 저장소 루트에서 실행하며, 먼저 Compose를 종료해 8080 포트를 비웁니다.

## 1. 이미지 빌드와 클러스터 생성

```bash
cd /mnt/c/dev/personal/infra/spring-gateway-docker-sample
bash scripts/new-local-env.sh
docker build -t local-backend:dev ./backend
docker build -t local-gateway:dev ./gateway
kind_node_image='kindest/node:v1.35.8@sha256:07b2536e30b803ed61d1677a79df6115f798ce64c80f9e22f6ed45afd09323c0'
kind create cluster --name gateway-lab --config k8s/kind-config.yaml --image "$kind_node_image"
kind load docker-image local-backend:dev local-gateway:dev --name gateway-lab
```

`kind-config.yaml`은 호스트의 `localhost:8080`을 Gateway Service의
NodePort `30080`으로 연결합니다. 따라서 별도 Gateway API 컨트롤러 없이도 샘플을
바로 호출할 수 있습니다.

kind v0.33.0 릴리스의 고정 이미지로 Envoy Gateway v1.9 지원 범위에 맞췄습니다.
이미 클러스터가 있다면 생성 명령은 건너뛰고 호환 버전을 확인합니다.
설치/호환표 근거는 [kind 학습 문서](../docs/learning/03-kind-kubernetes.md)에 있습니다.

kind는 Docker 컨테이너 안에 Kubernetes 노드를 구성하지만 lifecycle 환경은 로컬이므로 ConfigMap의 active
profile은 `local`, 배포 플랫폼은 `kubernetes`입니다. 공유 클러스터에서는 별도
overlay/배포 설정으로 `staging` 또는 `prod`를 사용해야 합니다.

## 2. Secret과 리소스 배포

Namespace를 먼저 만든 뒤 Git에서 제외된 `.env`로 Kubernetes Secret을 생성합니다.
명령행 인자에 실제 secret 값을 직접 적지 않습니다.

```bash
set -o pipefail
kubectl --context kind-gateway-lab apply -f k8s/base/namespace.yaml || exit 1
bash scripts/apply-local-secret.sh || exit 1
kubectl --context kind-gateway-lab apply -k k8s
kubectl --context kind-gateway-lab wait --for=condition=available deployment/redis deployment/backend deployment/gateway -n gateway-lab --timeout=180s
kubectl --context kind-gateway-lab get all -n gateway-lab
```

`apply-local-secret.sh`는 생성 스크립트의 따옴표 없는 `KEY=value` 형식을 읽으며,
`JWT_SECRET`, `DEMO_USERNAME`, `DEMO_PASSWORD` 세 키만 전송합니다. `.env`를 source하지 않습니다.
`JWT_ISSUER`, `JWT_AUDIENCE`, `JWT_TTL`은 `app-config`에서만 주입하고 Grafana 암호는 제외합니다.
Compose의 `.env` issuer/audience를 바꿨다면 kind ConfigMap 값도 의도에 맞게 따로 설정하세요.
기존 Secret에 불필요한 키가 남더라도 Deployment는 명시한 키만 참조합니다.
Secret의 base64는 암호화가 아니며 접근 권한과 로그 보호가 필요합니다.

공유 또는 운영 클러스터에서는 이 로컬 절차 대신 AWS Secrets Manager와 External
Secrets 같은 조직 표준 Secret 전달 방식을 사용합니다. 또한 `staging`/`prod`
profile에서는 로컬 `/auth/token` 발급기가 비활성화되므로 Cognito/Keycloak의
issuer/JWK 설정으로 보안 구성을 교체해야 합니다.

매니페스트의 NetworkPolicy는 Backend와 Redis에 대한 ingress를 Gateway Pod로
제한합니다. 실제 적용 여부는 사용하는 Kubernetes CNI의 NetworkPolicy 지원 여부도
확인해야 합니다. [허용/차단 대조 검증](../docs/learning/08-network-policy-validation.md)을 수행하기 전에는
정책이 실제 집행됐다고 기록하지 않습니다.

## 3. 호출

구성 파일은 `base/`와 `overlays/local`로 정리했습니다. 기존 `apply -k k8s`는
local overlay를 적용합니다. `overlays/staging`은 별도 Secret/인증 제공자가 필요한
렌더링 학습용입니다. [환경별 비교 실습](../docs/learning/04-operations-and-recovery.md)을 참고하세요.

이전 버전에서 업그레이드할 때 ConfigMap 이름은 `gateway-config`에서 `app-config`로
바뀝니다. Deployment 참조도 함께 변경했습니다. 예전 ConfigMap은 apply만으로 자동 삭제되지
않으므로 새 Deployment의 정상 기동과 참조 전환을 확인한 뒤 별도로 정리하세요.
이번 소스 변경에서는 클러스터 리소스를 수정하거나 삭제하지 않았습니다.

루트 `README.md`의 JWT 발급 및 API 호출 예제를 그대로 사용합니다.

## 선택: Kubernetes Gateway API

선택 진입점은 기존 Ingress 대신 Envoy Gateway와 Gateway API로 구성합니다.
기본 `kubectl apply -k k8s`에는 CRD가 필요한 리소스를 포함하지 않습니다.
별도 설치/호출/기존 Ingress 정리 절차는 [gateway-api/README.md](gateway-api/README.md)를
따릅니다. `cloud-provider-kind`, MetalLB 또는 추가 kind 포트 매핑은 필요하지 않습니다.

```text
기본: localhost:8080 → NodePort 30080 → Spring Cloud Gateway → Backend
선택: localhost:8888 → port-forward → Envoy Proxy → Spring Cloud Gateway → Backend
```

Envoy는 선택 경로의 HTTP 라우팅만 담당합니다. JWT 인증, CORS, 요청 헤더,
Redis Rate Limit은 Spring Cloud Gateway에 유지하며 Backend의 JWT 재검증도 유지합니다.
Gateway API 경로에는 `/api/**`와 로컬 `/auth/token`만 노출합니다.
기본 NodePort는 기존 동작과 비교하기 위한 로컬 전용 경로로 계속 열려 있으므로,
운영에서 edge-only 접근을 보장하는 구성으로 간주하면 안 됩니다.

## 종료

삭제 전 프로젝트 전용 클러스터인지 확인합니다. 클러스터의 Secret/앱/휘발성 데이터가 삭제되며
자세한 영향과 재개 절차는 [종료 가이드](../docs/learning/07-troubleshooting-and-cleanup.md)를 따릅니다.

```bash
kind delete cluster --name gateway-lab
```
