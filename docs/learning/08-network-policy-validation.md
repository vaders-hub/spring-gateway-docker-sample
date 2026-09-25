# 보충 실습: NetworkPolicy가 실제로 집행되는지 확인

[3단계로 돌아가기](03-kind-kubernetes.md) · [전체 순서](README.md)

## 이 보충 실습의 목적 / 왜 정책을 직접 검증하나요?

**NetworkPolicy YAML이 생성된 것과 실제 통신이 차단되는 것은 다릅니다.**
내부 Service나 namespace를 사용한다는 이유만으로 다른 Pod의 접근까지 막혔다고 판단하지 않도록,
정상 Gateway의 허용 경로와 임시 Pod의 비허용 경로를 비교합니다.
3단계 배포 후 수행하는 보충 실습이며, CNI의 정책 집행과 앱의 JWT 인증이 서로 다른 보호 계층임을 배웁니다.
완료 기준은 단순 timeout이 아니라 대조군·DNS·정책 지원 근거를 확인해
**집행 확인 / 차단 미확인 / 판정 불가** 중 관찰에 맞는 결론을 남기고 임시 Pod를 정리하는 것입니다.

## 전제와 판정 원칙

프로젝트 전용 `kind-gateway-lab`의 정상 배포에서만 수행합니다. 이 문서는 실행 절차이며
현재 PC에서 차단을 확인했다는 보고가 아닙니다. AWS 리소스는 사용하지 않습니다.

정책은 `k8s/base/network-policy.yaml`에 있습니다. 같은 namespace의 `app=gateway`
Pod만 Backend 8081 / Redis 6379에 접근하도록 선언합니다. 실제 집행에는 지원 CNI가
필요합니다. 정책은 가산적이므로 다른 허용 정책이 있으면 결과가 달라집니다.
[공식 NetworkPolicy 설명](https://kubernetes.io/docs/concepts/services-networking/network-policies/)

```bash
# [조회] 적용된 정책 객체 목록. 객체가 있다는 것만으로 차단됐다고 판단하지 않습니다.
kubectl --context kind-gateway-lab get networkpolicy -n gateway-lab
# [조회] 시스템 Pod와 노드/IP를 보고 CNI 구현을 찾습니다. 지원 여부는 버전 문서로 별도 확인합니다.
kubectl --context kind-gateway-lab get pods -n kube-system -o wide
# [조회] 앱과 Service 연결 대상이 정상인지 확인해 단순 서버 장애를 차단으로 오해하지 않게 합니다.
kubectl --context kind-gateway-lab get pods,svc,endpointslices -n gateway-lab
```

CNI Pod 이름만 보고 지원 여부를 단정하지 말고 해당 버전의 공식 기능 설명을 확인합니다.
정책의 ingress 방향만 검증합니다. 전체 egress, DNS 제한, TLS, 사용자 인증을 증명하지 않습니다.

## 1. 허용 경로의 대조군

실제 Gateway Pod에서 Backend health를 조회합니다. 임시 Pod에 `app=gateway`를 붙이면
Service selector/ReplicaSet과 충돌할 수 있으므로 그렇게 하지 않습니다.

```bash
# [대조 호출] 실제 Gateway Pod 안에서 Backend의 liveness 확인. -- 뒤 wget은 Pod 안에서 실행됩니다.
kubectl --context kind-gateway-lab exec -n gateway-lab deployment/gateway -- \
  wget -q -O - http://backend:8081/actuator/health/liveness
# [대조 호출] Gateway Pod 자신의 readiness 확인. 여기의 127.0.0.1은 노트북이 아니라 해당 Pod입니다.
# 현재 readiness에 Redis 점검이 포함되어 있으므로 Gateway → Redis 연결의 대조군으로 사용합니다.
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
# 블록 전체를 실행합니다. 별도 셸을 사용해 오류 종료와 임시 변수의 영향을 이 블록으로 제한합니다.
(
  # 오류/미정의 변수/파이프 오류 시 중단합니다. 아래 || true가 붙은 관찰 명령은 예외입니다.
  set -euo pipefail
  # 기존 Pod와 이름이 겹칠 가능성을 줄이도록 임시 이름을 만듭니다.
  probe_pod="policy-denied-$RANDOM-$RANDOM"
  # [임시 생성] 정책상 허용되지 않은 app=policy-denied Pod를 생성합니다.
  # --restart=Never는 Deployment 없이 Pod만 생성. 마지막 -- 뒤는 600초 대기할 컨테이너 명령입니다.
  # overrides는 API 토큰 자동 마운트 금지와 비root 실행 등 진단 Pod의 권한을 제한합니다.
  kubectl --context kind-gateway-lab run "$probe_pod" -n gateway-lab \
    --image=busybox:1.37.0 --restart=Never --labels=app=policy-denied \
    --overrides='{"spec":{"automountServiceAccountToken":false,"securityContext":{"runAsNonRoot":true,"runAsUser":10001,"seccompProfile":{"type":"RuntimeDefault"}}}}' \
    --command -- sleep 600
  # [자동 정리] 정상/오류로 이 셸이 끝나면 해당 임시 Pod만 삭제 요청. 삭제 완료까지 기다리지는 않습니다.
  trap 'kubectl --context kind-gateway-lab delete pod "$probe_pod" -n gateway-lab --ignore-not-found --wait=false' EXIT
  # [대기] 임시 Pod가 명령을 실행할 Ready 상태인지 최대 90초 확인합니다.
  kubectl --context kind-gateway-lab wait -n gateway-lab --for=condition=Ready \
    "pod/$probe_pod" --timeout=90s

  # [DNS 확인] 임시 Pod 안에서 Service 이름을 찾는지 확인. 이름 해석 성공은 TCP 연결 성공과 다릅니다.
  kubectl --context kind-gateway-lab exec -n gateway-lab "$probe_pod" -- nslookup backend
  kubectl --context kind-gateway-lab exec -n gateway-lab "$probe_pod" -- nslookup redis

  # [비허용 경로 테스트] Backend HTTP에 접근 시도. 응답(401/403 포함)이 오면 네트워크 접근이 가능합니다.
  # wget의 -S는 응답 헤더, -O -는 본문 출력, -T 3은 네트워크 대기 제한입니다.
  # || true는 예상 실패 뒤에도 다음 관찰을 계속하기 위한 것. 차단 성공으로 판정하는 구문이 아닙니다.
  kubectl --context kind-gateway-lab exec -n gateway-lab "$probe_pod" -- \
    wget -S -O - -T 3 http://backend:8081/actuator/health/liveness || true
  # [비허용 경로 테스트] Redis TCP 포트로 PING 전송. 전체 시도를 5초로 제한합니다.
  # +PONG/프로토콜 오류 등 응답이 오면 접근 가능. timeout만으로 정책 집행을 확정하지 않습니다.
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

## 구조·코드 이해 체크

실행 결과를 확인한 뒤 아래 항목을 관련 파일과 연결해 설명합니다. 모든 클래스를 암기하기보다 요청 한 건과 설정 한 개를 끝까지 추적하세요.

**읽을 파일:** [NetworkPolicy](../../k8s/base/network-policy.yaml) · [Gateway label](../../k8s/base/gateway.yaml) · [Backend Service](../../k8s/base/backend.yaml) · [Redis Service](../../k8s/base/redis.yaml)

- [ ] `spec.podSelector`는 보호 대상 Backend/Redis이고 `ingress.from.podSelector`는 접근을 허용할 같은 namespace의 Gateway임을 설명한다.
- [ ] 두 정책의 TCP 8081/6379와 Service targetPort/Pod label을 연결하고 ingress 방향만 제한한다는 범위를 설명한다.
- [ ] 여러 NetworkPolicy의 허용 규칙은 가산적임을 이해하고 객체 생성 성공과 지원 CNI의 실제 집행을 구분한다.
- [ ] 실제 Gateway의 허용 경로 → 임시 Pod의 DNS 확인 → 비허용 연결 시도 → 허용 경로 재확인 순서가 필요한 이유를 설명한다.
- [ ] HTTP 401/403도 네트워크 연결 성공 증거이며 timeout만으로 정책 차단을 확정할 수 없음을 설명한다.
- [ ] 임시 Pod에 `app=gateway` label을 붙이지 않는 이유와 trap이 해당 임시 Pod만 정리하는 범위를 설명한다.
- [ ] 관찰 결과를 집행 확인/차단 미확인/판정 불가 중 하나로 기록하고, 사용자 인증·TLS·전체 egress까지 검증했다고 확대하지 않는다.

**학습 기록:** 예상 경로 → 관찰한 HTTP 코드·로그·지표 → 근거 파일 → 복구 결과(해당 시) → 아직 설명하지 못하는 부분을 적습니다. 비밀번호·JWT·Secret 값은 적지 않습니다.

**다음 학습:** 이 문서는 3단계 보충입니다. 결과를 기록한 뒤 [4단계](04-operations-and-recovery.md)로 진행하며, CNI 변경이 필요하면 별도 실습으로 계획합니다.
