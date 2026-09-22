# 보충 실습: NetworkPolicy가 실제로 집행되는지 확인

[3단계로 돌아가기](03-kind-kubernetes.md) · [전체 순서](README.md)

## 전제와 판정 원칙

프로젝트 전용 `kind-gateway-lab`의 정상 배포에서만 수행합니다. 이 문서는 실행 절차이며
현재 PC에서 차단을 확인했다는 보고가 아닙니다. AWS 리소스는 사용하지 않습니다.

정책은 `k8s/base/network-policy.yaml`에 있습니다. 같은 namespace의 `app=gateway`
Pod만 Backend 8081 / Redis 6379에 접근하도록 선언합니다. 실제 집행에는 지원 CNI가
필요합니다. 정책은 가산적이므로 다른 허용 정책이 있으면 결과가 달라집니다.
[공식 NetworkPolicy 설명](https://kubernetes.io/docs/concepts/services-networking/network-policies/)

```bash
kubectl --context kind-gateway-lab get networkpolicy -n gateway-lab
kubectl --context kind-gateway-lab get pods -n kube-system -o wide
kubectl --context kind-gateway-lab get pods,svc,endpointslices -n gateway-lab
```

CNI Pod 이름만 보고 지원 여부를 단정하지 말고 해당 버전의 공식 기능 설명을 확인합니다.
정책의 ingress 방향만 검증합니다. 전체 egress, DNS 제한, TLS, 사용자 인증을 증명하지 않습니다.

## 1. 허용 경로의 대조군

실제 Gateway Pod에서 Backend health를 조회합니다. 임시 Pod에 `app=gateway`를 붙이면
Service selector/ReplicaSet과 충돌할 수 있으므로 그렇게 하지 않습니다.

```bash
kubectl --context kind-gateway-lab exec -n gateway-lab deployment/gateway -- \
  wget -q -O - http://backend:8081/actuator/health/liveness
kubectl --context kind-gateway-lab exec -n gateway-lab deployment/gateway -- \
  wget -q -O - http://127.0.0.1:8080/actuator/health/readiness
```

두 명령이 성공하고 UP이어야 합니다. 첫 번째는 Gateway→Backend, 두 번째는
Redis health를 포함하므로 Gateway→Redis 연결의 대조군입니다. 실패하면 차단 실습을
멈추고 앱/Service/DNS/Redis 상태를 먼저 복구합니다. health가 UP이어도 전체 업무 기능 검증은 아닙니다.

## 2. 허용되지 않은 Pod의 접속

WSL Bash에서 아래 블록 전체를 실행합니다. 임시 Pod 한 개만 생성하며 종료 시 그 Pod만
삭제합니다. 앱 데이터나 Secret은 읽지 않습니다. BusyBox 이미지 다운로드가 필요할 수 있습니다.

```bash
(
  set -euo pipefail
  probe_pod="policy-denied-$RANDOM-$RANDOM"
  kubectl --context kind-gateway-lab run "$probe_pod" -n gateway-lab \
    --image=busybox:1.37.0 --restart=Never --labels=app=policy-denied \
    --overrides='{"spec":{"automountServiceAccountToken":false,"securityContext":{"runAsNonRoot":true,"runAsUser":10001,"seccompProfile":{"type":"RuntimeDefault"}}}}' \
    --command -- sleep 600
  trap 'kubectl --context kind-gateway-lab delete pod "$probe_pod" -n gateway-lab --ignore-not-found --wait=false' EXIT
  kubectl --context kind-gateway-lab wait -n gateway-lab --for=condition=Ready \
    "pod/$probe_pod" --timeout=90s

  # DNS 해석 자체가 정상인지 먼저 확인
  kubectl --context kind-gateway-lab exec -n gateway-lab "$probe_pod" -- nslookup backend
  kubectl --context kind-gateway-lab exec -n gateway-lab "$probe_pod" -- nslookup redis

  # 응답이 오면 네트워크 접근이 가능하다는 증거. 실패만으로 정책 집행을 단정하지 않음.
  kubectl --context kind-gateway-lab exec -n gateway-lab "$probe_pod" -- \
    wget -S -O - -T 3 http://backend:8081/actuator/health/liveness || true
  kubectl --context kind-gateway-lab exec -n gateway-lab "$probe_pod" -- \
    timeout 5 sh -c 'printf "PING\r\n" | nc -w 3 redis 6379' || true
)
```

`|| true`는 예상한 차단 뒤에도 다음 관찰/정리를 수행하기 위한 것이며 성공 판정이 아닙니다.
Backend의 HTTP 응답(401/403도 포함)이나 Redis의 `+PONG`/프로토콜 오류가 보이면 접속이
가능한 것입니다. 허용되지 않은 Pod에서 연결이 되면 다른 정책과 selector를 확인하고
이 정책으로 접근이 차단된다고 기록하지 않습니다.

두 대상이 timeout이어도 서버 중단, DNS, CNI 장애 등이 원인일 수 있습니다.
즉시 1번의 허용 대조군을 다시 실행하고 EndpointSlice와 다른 정책도 함께 확인합니다.

| 관찰 | 기록할 결론 |
|---|---|
| 허용 경로 정상 + DNS 정상 + 차단 Pod만 연결 실패 + 지원 CNI/정책 확인 | 이 경로의 집행 확인 |
| 차단 Pod에서도 응답 수신 | 차단 미확인: 미지원 CNI/추가 허용 정책/selector 확인 |
| 허용 경로도 실패하거나 DNS 이상 | 판정 불가: 기반 연결부터 복구 |

검증하지 못했다면 **“NetworkPolicy 선언만 적용, 집행 미검증”**이라고 기록합니다.
미지원 CNI라면 기존 클러스터에 Calico를 곧바로 덧설치하지 않습니다. 별도 실습 클러스터의
kind 기본 CNI 비활성화와 호환 CNI 설치 계획을 먼저 정합니다.
기존 클러스터 삭제/재생성은 별도 결정이며 이 실습에 포함되지 않습니다.

- [ ] 허용 경로의 정상 결과를 전후로 확인했다.
- [ ] DNS 실패와 TCP 차단을 구분했다.
- [ ] 집행 확인 / 차단 미확인 / 판정 불가 중 하나를 기록했다.
- [ ] 임시 Pod가 정리되었고 앱 설정은 바꾸지 않았다.
