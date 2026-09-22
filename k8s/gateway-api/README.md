# 선택형 Gateway API 진입점: kind + Envoy Gateway

먼저 [기본 kind 배포](../README.md)를 완료합니다. 이 디렉터리는 기본 배포에
추가하는 선택 리소스이며, 애플리케이션/Secret/Namespace를 중복 생성하지 않습니다.
모든 명령은 저장소 루트의 WSL Bash에서 실행합니다.
[Windows + WSL2 도구 준비](../../docs/learning/00-windows-tool-installation.md)를 먼저 확인하세요.

## 구성과 경계

```text
localhost:8888
  → kubectl port-forward (127.0.0.1 전용)
  → Envoy Proxy Service:80 (ClusterIP)
  → Spring Cloud Gateway Service:8080
  → Backend Service:8081
```

- `GatewayClass/gateway-lab-envoy`: Envoy Gateway 컨트롤러 선택, cluster-scoped
- `Gateway/edge-gateway`: gateway-lab namespace의 HTTP listener
- `HTTPRoute/spring-gateway`: `/api` prefix와 `/auth/token` exact match
- `EnvoyProxy/local-edge-proxy`: kind용 ClusterIP Service 설정 (Envoy 전용 확장)

Gateway API의 Gateway와 기존 Spring 애플리케이션 Service `gateway`는 다른 리소스입니다.
HTTPRoute의 대상은 Backend가 아니라 Spring Cloud Gateway이므로 JWT/Redis 필터를
우회하지 않습니다. Envoy에서 prefix를 제거하지 않고, 기존 Spring `StripPrefix=1`만
적용합니다. `/actuator/**`는 이 HTTPRoute에 매칭되지 않아 edge 경로에서 404입니다.
HTTPRoute에서 발생하는 404 등 Envoy 오류는 Spring의 공통 오류 envelope가 아닙니다.

`localhost:8080` NodePort 경로는 비교용으로 유지합니다. 두 경로는 같은 Spring Pod와
Redis를 사용하므로 같은 JWT subject의 rate-limit 버킷을 공유합니다.
Envoy에는 JWT/CORS/rate-limit 정책을 중복 추가하지 않습니다.

## 1. 사전 조건

- Docker, kind, kubectl, Helm의 OCI chart 지원 버전
- 프로젝트 전용 `kind-gateway-lab` context, 기본 애플리케이션과 Secret 배포 완료
- 아래 고정 버전은 Envoy Gateway `v1.9.1`; 공식 v1.9 호환표의 Kubernetes
  `1.33~1.36`, Gateway API `v1.6.1` 조합을 기준으로 합니다.
- 설치 전에 `kubectl --context kind-gateway-lab version`으로 서버 버전을 확인합니다.
  다른 버전이면 호환표를 먼저 확인하고, 기존 클러스터를 임의로 삭제/재생성하지 않습니다.

신규 학습 클러스터의 재현 가능한 이미지 조합은
[kind 학습 문서](../../docs/learning/03-kind-kubernetes.md)를 따릅니다.

이 절차는 다른 Gateway API 설치가 없는 전용 kind 클러스터의 최초 설치용입니다.
기존 CRD/다른 컨트롤러가 있다면 덮어쓰거나 삭제하지 말고 소유권과 버전부터 확인합니다.
EKS 등 관리형 클러스터에 이 로컬 설치 명령을 그대로 적용하지 않습니다.

## 2. 컨트롤러와 CRD 설치

공식 Helm chart가 Gateway API 및 Envoy Gateway CRD를 함께 설치합니다.
예제 앱을 포함한 upstream quickstart.yaml은 배포하지 않습니다.

```bash
helm install gateway-lab-eg oci://docker.io/envoyproxy/gateway-helm \
  --version v1.9.1 \
  --kube-context kind-gateway-lab \
  --namespace envoy-gateway-system --create-namespace \
  --wait --timeout 5m

kubectl --context kind-gateway-lab wait --for=condition=Established \
  crd/gatewayclasses.gateway.networking.k8s.io \
  crd/gateways.gateway.networking.k8s.io \
  crd/httproutes.gateway.networking.k8s.io \
  crd/envoyproxies.gateway.envoyproxy.io --timeout=60s
```

각 명령이 성공한 뒤 다음 단계로 진행합니다. 이미 설치했다면 `helm install`을
반복하지 않고 `helm --kube-context kind-gateway-lab list -n envoy-gateway-system`으로
확인합니다. 업그레이드는 CRD 업그레이드 정책도 함께 확인해야 합니다.

## 3. Gateway와 Route 적용

```bash
kubectl --context kind-gateway-lab apply -k k8s/gateway-api
kubectl --context kind-gateway-lab wait --for=condition=Accepted \
  gatewayclass/gateway-lab-envoy --timeout=60s
kubectl --context kind-gateway-lab wait --for=condition=Programmed \
  gateway/edge-gateway -n gateway-lab --timeout=180s
kubectl --context kind-gateway-lab get httproute spring-gateway -n gateway-lab -o yaml
```

HTTPRoute의 `status.parents`에서 `edge-gateway`에 대한 `Accepted=True`,
`ResolvedRefs=True` 및 현재 `metadata.generation`에 해당하는 `observedGeneration`을
확인합니다. 문제가 있으면 Gateway와 HTTPRoute의 `kubectl describe`부터 확인합니다.
`NoMatchingParent`, `BackendNotFound`, `RefNotPermitted` 등을 무시하고 진행하지 않습니다.

## 4. 로컬 포트 열기

Envoy 컨트롤러가 생성한 프록시 Service 이름은 해시를 포함하므로 이름을 추측하지 않고
owning-gateway label로 조회합니다. 컨트롤러 자체의 Service에 연결하면 안 됩니다.
아래 명령은 하나의 WSL Bash 터미널에서 실행하고, 포트 전달이 유지되는 동안 창을 열어둡니다.

```bash
edge_selector='gateway.envoyproxy.io/owning-gateway-namespace=gateway-lab,gateway.envoyproxy.io/owning-gateway-name=edge-gateway'
edge_service_json=$(kubectl --context kind-gateway-lab get service \
  -n envoy-gateway-system --selector "$edge_selector" -o json) || exit 1
edge_service_name=$(printf '%s' "$edge_service_json" |
  jq -er 'if (.items | length) == 1 then .items[0].metadata.name else error("Expected exactly one Envoy proxy Service") end') || exit 1

kubectl --context kind-gateway-lab wait --for=condition=Available deployment \
  -n envoy-gateway-system --selector "$edge_selector" --timeout=180s || exit 1
kubectl --context kind-gateway-lab port-forward \
  -n envoy-gateway-system "service/$edge_service_name" 8888:80 --address 127.0.0.1
```

별도 LoadBalancer, cloud-provider-kind, MetalLB, kind 노드 포트 매핑이 필요하지 않습니다.
종료는 `Ctrl+C`이며, 프록시 Pod가 교체되면 port-forward를 다시 연결해야 할 수 있습니다.

## 5. 호출 확인 (배포 후 수행)

다른 WSL Bash 터미널에서 실행합니다. secret/token 값은 출력하지 않습니다.

```bash
set +x
source scripts/local-api.sh
lab_login http://localhost:8888
lab_api GET /api/hello
lab_api POST /api/echo '{"name":"edge-test","value":25}'
```

추가 확인 기준:

| 요청 | 기대 결과 |
|---|---|
| 인증 없이 `/api/hello` | Spring의 401 |
| 잘못된 Bearer 토큰으로 `/api/hello` | Spring의 401 |
| 유효한 JWT로 `/api/hello`, `/api/echo` | rate-limit 여유가 있으면 200 |
| 같은 사용자로 burst 초과 호출 | Spring Redis RateLimiter의 429 |
| `/actuator/health` 또는 `/` | Envoy의 404 |

이 문서는 실행 절차와 기대 결과이며, 실제 설치/통합 테스트 통과 기록은 아닙니다.

## 이전 Ingress를 이미 배포한 경우

기본 Kustomization에서 Ingress를 제거해도 기존 클러스터 리소스는 자동 삭제되지 않습니다.
먼저 위 경로가 동작하는지 확인하고, 아래 조회로 기존 리소스가 이 샘플의
`gateway:8080`을 향하는 Ingress인지 확인한 뒤에만 삭제합니다.

```bash
kubectl --context kind-gateway-lab get ingress gateway -n gateway-lab -o yaml
# 위 리소스가 이 샘플의 이전 Ingress인 경우에만 실행
kubectl --context kind-gateway-lab delete ingress gateway -n gateway-lab --ignore-not-found
```

다른 프로젝트의 Ingress나 컨트롤러는 삭제하지 않습니다.

## 선택 리소스만 해제

port-forward를 먼저 종료한 뒤 다음을 실행합니다. 기본 앱, Redis, Secret, NodePort는 유지됩니다.

```bash
kubectl --context kind-gateway-lab delete -k k8s/gateway-api --ignore-not-found
```

공유될 수 있는 CRD/컨트롤러는 이 명령으로 제거하지 않습니다. 프로젝트 전용 kind 전체를
종료할 때는 [상위 문서](../README.md)의 클러스터 종료 절차를 사용합니다.

## 운영 전환과 참고

이 구성은 loopback port-forward를 사용하는 로컬 HTTP 샘플입니다. 운영에서는 TLS,
명시적인 hostname, 인증서 Secret/갱신, Spring Service의 ClusterIP 전환 및 edge에서만
접근 가능한 NetworkPolicy/보안그룹을 별도 설계합니다. `/auth/token` 데모 경로도 제거합니다.
AWS API Gateway 도입 여부와 정책 책임은 [진입점 설계](../../docs/traffic-entry.md)를 따릅니다.

- [Envoy Gateway Helm 설치](https://gateway.envoyproxy.io/docs/install/install-helm/)
- [버전 호환표](https://gateway.envoyproxy.io/news/releases/matrix/)
- [프록시 설정과 Gateway 연결](https://gateway.envoyproxy.io/docs/tasks/operations/customize-envoyproxy/)
- [공식 port-forward 예시](https://gateway.envoyproxy.io/docs/tasks/quickstart/)
