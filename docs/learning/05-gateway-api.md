# 5단계: Gateway API와 API Gateway 역할 구분

[전체 순서](README.md) · 이전: [운영 실습](04-operations-and-recovery.md) · 다음: [AWS/Spring 확장](06-aws-and-spring-expansion.md)

## 목표

Kubernetes의 진입 설정과 Spring의 API 정책을 분리합니다. AWS API Gateway 서비스는
생성하지 않습니다. Envoy 컨트롤러/프록시는 로컬 kind에서 실행합니다.

```text
GatewayClass → 사용할 컨트롤러 선택
Gateway      → listener와 연결 가능한 Route 범위
HTTPRoute    → 경로를 Spring Service로 연결
EnvoyProxy   → 로컬 프록시 Service를 ClusterIP로 설정

Client:8888 → Envoy Proxy → Spring Cloud Gateway → Backend
                              └→ Redis Rate Limit
```

Gateway API는 설정 API이며 실제 트래픽은 Envoy Proxy가 처리합니다.
Spring Cloud Gateway는 JWT·CORS·사용자별 요청 제한을 담당하는 앱입니다.
[Gateway API FAQ](https://gateway-api.sigs.k8s.io/docs/faq/)

## 구현과 기동 순서

상세 실행 명령의 단일 기준은 [Gateway API 설치/실행 문서](../../k8s/gateway-api/README.md)입니다.
아래 순서로 해당 문서의 명령을 실행합니다. 기본 kind 앱이 먼저 정상이어야 합니다.

1. `kubectl --context kind-gateway-lab version`으로 서버 버전과 Envoy 호환표 확인.
2. Helm으로 고정 버전 Envoy Gateway와 CRD 설치. upstream 예제 앱은 설치하지 않음.
3. `kubectl --context kind-gateway-lab apply -k k8s/gateway-api` 실행.
4. GatewayClass Accepted, Gateway Programmed, HTTPRoute Accepted/ResolvedRefs 확인.
5. 생성된 Envoy 프록시 Service를 label로 조회하고 loopback `8888:80` port-forward.
6. 별도 창에서 설치 문서의 JWT/API 호출 수행. port-forward 창은 유지.

구현을 읽는 순서는 `gateway-class.yaml` → `envoy-proxy.yaml` → `edge-gateway.yaml`
→ `http-route.yaml`입니다. 네 리소스의 이름·namespace·참조를 직접 연결해 봅니다.
`HTTPRoute.backendRefs`는 `gateway:8080`이어야 합니다. Backend를 바로 연결하면
Spring의 사용자별 Redis 제한 등 gateway 정책을 우회하게 됩니다.

## 비교 실험과 체크

| 요청 | NodePort 8080 | Envoy 8888 |
|---|---|---|
| `/api/hello`, 토큰 없음 | 401 | 401 |
| `/api/hello`, 유효 토큰/버킷 여유 | 200 | 200 |
| `/api/echo`, 유효 요청/토큰 | 200 | 200 |
| 동일 subject로 burst 초과 | 429 가능 | 같은 Redis 버킷에서 429 가능 |
| `/actuator/health` | Spring health 응답 | HTTPRoute 미일치 404 |

- [ ] Envoy의 404와 Spring의 401/400/429를 구별했다.
- [ ] `/api` prefix가 두 번 제거되지 않으며 Authorization이 Spring에 도달한다.
- [ ] NetworkPolicy의 `Ingress`는 트래픽 방향이며 옛 `kind: Ingress` 리소스와 다르다.
- [ ] 8080이 유지되므로 Envoy가 유일한 진입점이라는 보장은 없음을 이해했다.
- [ ] TLS, 외부 DNS, AWS ALB를 검증한 것은 아님을 기록했다.

선택 구현 실습: HTTPRoute의 `/api` prefix match를 `/api/hello` exact match로 바꿔
적용합니다. hello는 유지되고 echo는 Envoy 404가 되는지 관찰한 후 원래 설정으로 복구합니다.
`/auth/token` match는 유지해야 새 토큰을 발급할 수 있습니다.
수정/복구마다 Route의 status와 generation을 확인합니다.

기존 클러스터에 남은 Ingress 정리와 선택 리소스 종료는 설치 문서의 해당 절차를 따릅니다.
Envoy만 내릴 때 기본 앱/Redis/Secret이나 공유 CRD까지 삭제하지 않습니다.

공식 자료: [Envoy 설치](https://gateway.envoyproxy.io/docs/install/install-helm/),
[Envoy 호환표](https://gateway.envoyproxy.io/news/releases/matrix/),
[Ingress의 현재 권장 방향](https://kubernetes.io/docs/concepts/services-networking/ingress/)
