# 3단계: 동일 앱을 kind Kubernetes에 배포

[전체 순서](README.md) · 이전: [관측성](02-observability.md) · 다음: [운영 실습](04-operations-and-recovery.md)

## 목표 / 매니페스트 읽기

| 파일 | 학습할 내용 |
|---|---|
| `k8s/kind-config.yaml` | Docker 노드와 host 8080 → node 30080 포트 매핑 |
| `k8s/base/namespace.yaml` | 이 프로젝트의 리소스 범위 |
| `k8s/base/configmap.yaml` | 공개 가능한 환경 설정, local profile |
| `k8s/base/gateway.yaml` | Deployment와 NodePort Service, probe, 자원/보안 설정 |
| `k8s/base/backend.yaml` | 내부 ClusterIP Service와 JWT secret 참조 |
| `k8s/base/redis.yaml` | 내부 Redis와 휘발성 emptyDir |
| `k8s/base/network-policy.yaml` | Backend/Redis에 접근 가능한 Pod 선택 |
| `k8s/kustomization.yaml` | 기본 리소스 조합; Gateway API는 별도 |

Service는 안정적인 접근 주소, Pod는 교체 가능한 실행 단위입니다. Pod IP를
Spring `BACKEND_URL`에 하드코딩하지 않습니다. cluster DNS의 `backend:8081`을 사용합니다.

## 3-1. Compose 종료와 이미지 준비

모든 명령은 WSL Bash에서 실행합니다. Windows에서 생성한 클러스터가 이미 있다면
[WSL kubeconfig 연결 절차](07-troubleshooting-and-cleanup.md)를 먼저 확인합니다.

2단계의 종료 명령으로 Compose를 내립니다. `kind get clusters`에 이미 `gateway-lab`이
있으면 무조건 재생성하지 말고 context/서버 버전/배포 상태를 먼저 확인합니다.

```bash
kind get clusters
kubectl config get-contexts
docker build -t local-backend:dev ./backend
if [ "$?" -ne 0 ]; then printf '%s\n' 'Backend image build failed.' >&2; exit 1; fi
docker build -t local-gateway:dev ./gateway
if [ "$?" -ne 0 ]; then printf '%s\n' 'Gateway image build failed.' >&2; exit 1; fi
```

Compose 이미지는 태그가 다를 수 있으므로 kind 매니페스트가 기대하는 이름으로 빌드합니다.
Docker Desktop에 이미지가 존재하는 것과 kind 노드 안에 이미지가 있는 것은 다릅니다.

## 3-2. 새 클러스터 생성과 이미지 로드

기준 조합: kind `v0.33.0`, Kubernetes `v1.35.8`, 선택형 Envoy Gateway `v1.9.1`.
kind 최신 기본 이미지를 무조건 사용하지 않고 Envoy 지원 범위와 겹치는 이미지를 고정합니다.
기존 호환 클러스터를 사용한다면 생성 명령은 건너뜁니다.

```bash
kind_node_image='kindest/node:v1.35.8@sha256:07b2536e30b803ed61d1677a79df6115f798ce64c80f9e22f6ed45afd09323c0'
kind create cluster --name gateway-lab --config k8s/kind-config.yaml --image "$kind_node_image"
if [ "$?" -ne 0 ]; then printf '%s\n' 'kind creation failed.' >&2; exit 1; fi
kubectl --context kind-gateway-lab cluster-info
if [ "$?" -ne 0 ]; then printf '%s\n' 'Check WSL networking before continuing.' >&2; exit 1; fi
kubectl --context kind-gateway-lab get nodes -o wide
kind load docker-image local-backend:dev local-gateway:dev --name gateway-lab
if [ "$?" -ne 0 ]; then printf '%s\n' 'Image load failed.' >&2; exit 1; fi
```

이미지/digest는 [kind v0.33.0 릴리스](https://github.com/kubernetes-sigs/kind/releases/tag/v0.33.0),
Envoy 조합은 [호환표](https://gateway.envoyproxy.io/news/releases/matrix/)를 기준으로 합니다.
kubectl은 서버 버전과 지원되는 version skew 범위를 맞추세요.
[kubectl 설치 안내](https://kubernetes.io/docs/tasks/tools/install-kubectl-linux/)

## 3-3. Secret과 애플리케이션 배포

1단계에서 생성한 `.env`를 사용합니다. 실제 값을 YAML 파일로 저장하지 않습니다.

```bash
test -f .env || { printf '%s\n' 'Complete local environment setup first.' >&2; exit 1; }
set -o pipefail
kubectl --context kind-gateway-lab apply -f k8s/base/namespace.yaml
if [ "$?" -ne 0 ]; then printf '%s\n' 'Namespace creation failed.' >&2; exit 1; fi

# 필요한 세 키만 파이프로 전달. tee/파일 저장/콘솔 출력 금지
bash scripts/apply-local-secret.sh
if [ "$?" -ne 0 ]; then printf '%s\n' 'Secret apply failed.' >&2; exit 1; fi

kubectl kustomize k8s
kubectl --context kind-gateway-lab apply -k k8s
if [ "$?" -ne 0 ]; then printf '%s\n' 'Application apply failed.' >&2; exit 1; fi
kubectl --context kind-gateway-lab wait --for=condition=Available \
  deployment/redis deployment/backend deployment/gateway -n gateway-lab --timeout=180s
kubectl --context kind-gateway-lab get pods,svc,deploy -n gateway-lab
```

`apply-local-secret.sh`는 생성 스크립트의 따옴표 없는 `KEY=value` 형식을 읽으며,
`JWT_SECRET`, `DEMO_USERNAME`, `DEMO_PASSWORD` 세 키만 전송합니다. `.env`를 source하지 않습니다.
`JWT_ISSUER`, `JWT_AUDIENCE`, `JWT_TTL`은 `app-config`에서만 주입하고 Grafana 암호는 제외합니다.
Compose의 `.env` issuer/audience를 바꿨다면 kind ConfigMap 값도 의도에 맞게 따로 설정하세요.
기존 Secret에 불필요한 키가 남더라도 Deployment는 명시한 키만 참조합니다.
Secret의 base64는 암호화가 아니며 접근 권한과 로그 보호가 필요합니다.

`apply` 성공이 앱 기동 성공은 아닙니다. wait가 실패하면 먼저 원인을 진단합니다.
Kubernetes Secret의 base64는 암호화가 아니며 Secret을 읽을 수 있는 권한을 제한해야 합니다.
로컬 파일 보호, RBAC, etcd 저장 시 암호화, 운영 Secret 관리는 각각 별개의 검토 항목입니다.
[Kubernetes Secret](https://kubernetes.io/docs/concepts/configuration/secret/)

## 3-4. 연결 확인

```bash
curl -sS -o /dev/null -w '%{http_code}' http://localhost:8080/actuator/health/readiness
kubectl --context kind-gateway-lab get endpointslices -n gateway-lab \
  -l kubernetes.io/service-name=backend
```

readiness 200을 확인한 뒤 [1단계 JWT/API 절차](01-setup-and-compose.md)의
`source scripts/local-api.sh`와 `lab_login http://localhost:8080`부터 다시 실행합니다. Compose에서 발급한 토큰을
무조건 재사용하지 않고 현재 Secret의 키/issuer와 만료를 기준으로 새로 발급합니다.

## 3-5. NetworkPolicy 집행 확인

[NetworkPolicy 검증](08-network-policy-validation.md)에서 기존 Gateway의 허용 경로와
별도 임시 Pod의 차단 경로를 대조합니다. 정책 객체 생성과 실제 차단은 다릅니다.
단순 timeout만으로 집행 성공이라고 판단하지 않습니다.

## 체크 / 다음 단계

- [ ] Node Ready, 3개 Deployment Available, 각 Pod Ready를 확인했다.
- [ ] Gateway만 NodePort이고 Backend/Redis는 ClusterIP임을 확인했다.
- [ ] Gateway readiness 200과 `/api/hello` JWT 호출을 확인했다.
- [ ] Secret 값 전체를 출력하지 않고 존재와 참조만 확인했다.
- [ ] `local`은 lifecycle profile, `kubernetes`는 배포 플랫폼임을 구분했다.
- [ ] 외부 노출이 없다는 것이 클러스터 내부 접근 제어까지 보장하지는 않음을 이해했다.
- [ ] kind 이미지는 ECR 없이 로컬에서 로드했음을 설명할 수 있다.

기본 단일 노드 클러스터는 다중 AZ 고가용성을 재현하지 않습니다. Redis는 persistence가
꺼져 있고 emptyDir을 사용하므로 복구 후 데이터 유지가 보장되지 않습니다.
kind는 그대로 유지한 채 4단계로 넘어갑니다. 종료/재개는 [정리 가이드](07-troubleshooting-and-cleanup.md)를 참고합니다.
