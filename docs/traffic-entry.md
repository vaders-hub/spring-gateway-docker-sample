# 네트워크 진입점과 API 정책의 책임

## 현재 로컬 구성

| 계층 | 담당 | 담당하지 않는 것 |
|---|---|---|
| Gateway API + Envoy Proxy (선택) | HTTP listener, `/api`와 `/auth/token` 라우팅 | JWT/CORS/Redis Rate Limit 중복 구현 |
| Spring Cloud Gateway | JWT 검증, scope, CORS, 요청 ID/사용자 헤더, Redis Rate Limit, `/api` prefix 제거 | Oracle 접근, 업무 처리 |
| Backend | JWT 재검증, 업무 API, 향후 Oracle/JPA/MyBatis | 외부 네트워크 진입점 |

기본 `k8s`는 NodePort로 Spring에 직접 연결합니다. `k8s/gateway-api`는 별도 선택 경로이며
기본 Kustomization에 포함하지 않아 CRD가 없어도 기존 학습 흐름을 유지합니다.
`kind: Gateway`는 프록시를 설정하는 리소스이지 Spring Cloud Gateway 애플리케이션이 아닙니다.
`EnvoyProxy`는 ClusterIP 노출을 선택하기 위한 구현체 전용 확장이고, 나머지 진입 리소스는
Gateway API v1입니다. 구현체 교체 시 controllerName과 프록시 전용 설정은 검토해야 합니다.

NodePort의 `localhost:8080`과 port-forward의 `localhost:8888`은 로컬 비교용입니다.
NodePort가 남아 있으므로 Envoy가 유일한 진입점이라는 보안 보장은 하지 않습니다.
Envoy HTTPRoute는 Backend로 직접 연결하지 않으며, 두 경로 모두 Spring 인증/요청 제한을 거칩니다.
Backend/Redis NetworkPolicy의 `Ingress`는 들어오는 트래픽 방향을 뜻하므로 유지합니다.
이는 삭제한 `kind: Ingress` 리소스와 다릅니다. NetworkPolicy 집행은 CNI 지원이 필요합니다.
선언만으로 차단을 보장하지 않으며 [허용/차단 대조 검증](learning/08-network-policy-validation.md)을 수행합니다.

## EKS 전환 선택지

### 관리형 API Gateway가 필요하지 않은 경우

```text
Client → ALB → Spring Cloud Gateway → Backend
```

AWS Load Balancer Controller의 Gateway API 지원을 이용해 ALB와 라우팅을 구성할 수 있습니다.
Gateway API 지원 버전/기능 범위를 확인하고 AWS용 GatewayClass와 네트워크 설정을 별도로 둡니다.
kind의 Envoy 전용 설정을 ALB에 그대로 재사용하지 않습니다.

### AWS API Gateway를 사용하는 경우

```text
Client → AWS API Gateway → VPC Link → Internal ALB
       → Spring Cloud Gateway (필요한 경우) → Backend
```

관리형 인증/요청 제어가 필요한 경우의 선택지이며, API Gateway는 EKS의 필수 구성요소가 아닙니다.
HTTP API와 REST API의 기능/제한을 비교한 뒤 선택합니다. HTTP API의 private integration은
VPC Link를 통해 ALB/NLB 등과 연결할 수 있습니다.

정책을 옮길 때는 다음을 구분합니다.

- JWT: 현재 두 앱의 issuer/audience/scope 검증을 기준으로 외부 IdP의 issuer/JWK/키 회전으로 전환.
  audience는 대상 API를, scope는 허용 작업을 구분합니다. 현재 HS256 데모 secret을 AWS JWT
  Authorizer에 그대로 이식하는 방식으로 가정하지 않습니다.
- Rate limit: AWS의 route/stage throttling이 현재 Redis의 JWT subject별 버킷과 같은
  정책이라고 가정하지 않습니다. 사용자/테넌트 단위 제한이 필요하면 별도 유지합니다.
- CORS와 경로 rewrite: 각 기능의 소유 계층을 하나로 정하고 중복 헤더/이중 prefix 제거를
  피합니다. JWT의 방어적 재검증은 불필요한 설정 중복과 구분합니다.
- Spring Gateway: 필요한 사용자 정의 필터/정책이 남을 때 유지합니다. 모든 책임이
  외부로 이전되면 제거를 검토하되, Backend의 인증 경계와 우회 경로를 먼저 검증합니다.
- 외부 노출: 운영 Spring Service는 ClusterIP로 제한하고 TLS/hostname/NetworkPolicy와
  AWS 보안그룹을 함께 설정합니다. 로컬 NodePort/데모 발급 경로는 운영에 가져가지 않습니다.

AWS 리소스 생성, 비용 발생 작업, OIDC 전환 및 정책 이전은 이번 변경에 포함하지 않습니다.

## 공식 자료

- [Kubernetes Ingress와 Gateway 권장 방향](https://kubernetes.io/docs/concepts/services-networking/ingress/)
- [Gateway API와 API Gateway의 차이](https://gateway-api.sigs.k8s.io/docs/faq/)
- [AWS Load Balancer Controller의 Gateway API 구성](https://aws.amazon.com/blogs/networking-and-content-delivery/streamline-your-amazon-eks-deployments-with-gateway-api-support-for-aws-load-balancer-controller-and-amazon-vpc-lattice/)
- [AWS HTTP API의 private integration](https://docs.aws.amazon.com/apigateway/latest/developerguide/http-api-develop-integrations-private.html)
- [AWS HTTP API JWT Authorizer](https://docs.aws.amazon.com/apigateway/latest/developerguide/http-api-jwt-authorizer.html)
- [AWS HTTP API throttling 범위](https://docs.aws.amazon.com/apigateway/latest/developerguide/http-api-throttling.html)
