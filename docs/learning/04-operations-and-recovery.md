# 4단계: 배포·설정 변경·장애와 복구

[전체 순서](README.md) · 이전: [kind 배포](03-kind-kubernetes.md) · 다음: [Gateway API](05-gateway-api.md)

## 이번 단계의 목적 / 왜 장애와 복구를 실습하나요?

3단계에서 앱을 띄웠다면, 이제는 **설정 변경이나 장애가 생겨도 상태를 판단하고 정상으로 되돌리는 방법**을 배웁니다.
최초 기동 성공만으로는 복제본 증가, 설정 반영, 잘못된 배포, 의존 서비스 중단 때의 동작을 알 수 없기 때문입니다.
로컬 kind에서 한 가지씩 변경하고 Pod·Service·로그·API 응답을 비교해 Kubernetes의 조치와 Spring의 동작을 연결합니다.
완료 기준은 명령 실행 자체가 아니라 **변경 전후의 차이를 설명하고 복제본·설정·인증 API를 정상 상태로 복구하는 것**입니다.
단일 노드 실습이므로 실제 EKS의 다중 AZ 고가용성이나 운영 무중단을 입증하는 단계는 아닙니다.

전제: 3단계가 정상이며 프로젝트 전용 `kind-gateway-lab`만 사용합니다.
한 번에 하나의 실습을 수행하고 복구한 뒤 다음으로 넘어갑니다.
아래는 실행 예정 절차이며 무중단/복구가 검증됐다는 보고가 아닙니다.

## 4-1. 복제본과 Deployment

```bash
kubectl --context kind-gateway-lab scale deployment/backend -n gateway-lab --replicas=2
kubectl --context kind-gateway-lab rollout status deployment/backend -n gateway-lab --timeout=180s
kubectl --context kind-gateway-lab get pods -n gateway-lab -l app=backend -o wide
kubectl --context kind-gateway-lab get endpointslices -n gateway-lab \
  -l kubernetes.io/service-name=backend
```

ReplicaSet이 복제본 수를 맞추고 Service는 준비된 Pod를 대상으로 연결함을 확인합니다.
두 Pod가 같은 kind 노드에 있으므로 두 서버/AZ에 분산된 고가용성은 아닙니다.
HTTP 연결 재사용 때문에 요청마다 교대로 분산된다고 단정하지 않습니다.

복구:

```bash
kubectl --context kind-gateway-lab scale deployment/backend -n gateway-lab --replicas=1
kubectl --context kind-gateway-lab rollout status deployment/backend -n gateway-lab --timeout=180s
```

체크: [ ] 1→2→1 확인 [ ] 수동 scale과 HPA 차이 이해.
HPA/`kubectl top`은 metrics API 등 준비가 필요하며 현재 샘플에는 metrics-server를 설치하지 않았습니다.
[Deployment](https://kubernetes.io/docs/concepts/workloads/controllers/deployment/),
[HPA](https://kubernetes.io/docs/concepts/workloads/autoscaling/horizontal-pod-autoscale/)

## 4-2. ConfigMap 변경은 실행 프로세스에 자동 반영되지 않음

구현: 편집기로 `k8s/overlays/local/rate-limit-patch.yaml`의 `RATE_LIMIT_BURST_CAPACITY: "5"`를 `"3"`으로
바꿉니다. replenish rate는 2로 유지합니다. Secret은 수정하지 않습니다.

```bash
kubectl --context kind-gateway-lab apply -k k8s/overlays/local
kubectl --context kind-gateway-lab exec deployment/gateway -n gateway-lab -- printenv RATE_LIMIT_BURST_CAPACITY
kubectl --context kind-gateway-lab rollout restart deployment/gateway -n gateway-lab
kubectl --context kind-gateway-lab rollout status deployment/gateway -n gateway-lab --timeout=180s
kubectl --context kind-gateway-lab exec deployment/gateway -n gateway-lab -- printenv RATE_LIMIT_BURST_CAPACITY
```

처음은 기존 프로세스 값 5, rollout 후는 3이 기대됩니다. 비밀값이 아닌 단일 변수만 조회합니다.
이후 JWT/API burst 실험으로 동작을 비교합니다. 관찰값은 호출 속도와 버킷 잔량에 영향을 받습니다.

복구: 소스 값을 `"5"`로 돌리고 위 apply/restart/status를 다시 수행합니다.
Deployment `rollout undo`만으로 ConfigMap의 내용까지 복구되지는 않습니다.
체크: [ ] 소스·ConfigMap·Pod 환경변수·실제 동작 네 지점을 구분했다.
[ConfigMap 환경변수 갱신](https://kubernetes.io/docs/tutorials/configuration/updating-configuration-via-a-configmap/)

## 4-3. Redis 장애: liveness와 readiness

이 실습은 휘발성 Rate Limit 데이터를 잃게 합니다. 업무 DB/운영 Redis에서 수행하지 않습니다.
먼저 별도 WSL Bash 터미널에서 Gateway Pod로 loopback port-forward를 엽니다.

```bash
kubectl --context kind-gateway-lab port-forward -n gateway-lab \
  deployment/gateway 8889:8080 --address 127.0.0.1
```

다른 창에서 장애와 관찰:

```bash
kubectl --context kind-gateway-lab scale deployment/redis -n gateway-lab --replicas=0
kubectl --context kind-gateway-lab get pods -n gateway-lab
curl -sS --max-time 10 -o /dev/null -w '%{http_code}' http://localhost:8889/actuator/health/liveness
curl -sS --max-time 10 -o /dev/null -w '%{http_code}' http://localhost:8889/actuator/health/readiness
kubectl --context kind-gateway-lab logs deployment/gateway -n gateway-lab --since=3m --tail=80
```

Redis 중단과 probe 반영에는 시간이 필요합니다. 기대: liveness 200, readiness 503,
Gateway Pod Ready false. Redis 장애만으로 liveness까지 실패해 재시작 루프가 생기지 않는지 봅니다.
Service 경로인 8080은 준비된 endpoint가 사라져 접속 실패가 날 수 있으므로, readiness의
503을 직접 관찰할 때는 위 Pod port-forward를 사용합니다.

중요: 현재 RedisRateLimiter 5.0.3은 Redis 호출 오류 시 허용 fallback이 있습니다.
따라서 readiness 제외만으로 요청 단위 fail-closed가 보장되지는 않습니다. 유효 토큰으로
Pod port-forward 8889에 `/api/hello`를 호출하면 Redis 장애 중에도 200이 나올 수 있습니다.
Compose 직접 접근, 기존 연결, probe 반영 전 구간도 구분해 관찰합니다.
엄격한 차단은 후속 구현 과제로 남겨두며 RedisRateLimiter의 기본 동작은 변경하지 않았습니다.
[장애 정책 설계](../resilience-policy.md)에 구현 선택지와 429/503 응답 기준을 정리했습니다.
[해당 버전의 공식 소스](https://github.com/spring-cloud/spring-cloud-gateway/blob/v5.0.3/spring-cloud-gateway-server-webflux/src/main/java/org/springframework/cloud/gateway/filter/ratelimit/RedisRateLimiter.java)

복구 (실패했더라도 반드시 수행):

```bash
kubectl --context kind-gateway-lab scale deployment/redis -n gateway-lab --replicas=1
kubectl --context kind-gateway-lab rollout status deployment/redis -n gateway-lab --timeout=180s
kubectl --context kind-gateway-lab wait --for=condition=Ready pod -l app=gateway \
  -n gateway-lab --timeout=180s
curl -sS -o /dev/null -w '%{http_code}' http://localhost:8080/actuator/health/readiness
```

port-forward 창에서 Ctrl+C. 체크: [ ] 장애/복구 시각 기록 [ ] readiness 200 복귀
[ ] rate-limit 버킷 초기화 이해.
[Probe 공식 설명](https://kubernetes.io/docs/tasks/configure-pod-container/configure-liveness-readiness-startup-probes/)

## 4-4. 잘못된 설정으로 rollout 실패시키고 복구

Backend의 정상 revision을 먼저 보관합니다. 현재 rollout이 끝난 정상 상태에서만 수행합니다.

```bash
kubectl --context kind-gateway-lab rollout status deployment/backend -n gateway-lab --timeout=180s
if [ "$?" -ne 0 ]; then printf '%s\n' 'Baseline deployment is not healthy. Do not start the failure drill.' >&2; exit 1; fi
backend_revision=$(kubectl --context kind-gateway-lab get deployment backend -n gateway-lab \
  -o jsonpath='{.metadata.annotations.deployment\.kubernetes\.io/revision}')
if [ "$?" -ne 0 ] || [[ ! "$backend_revision" =~ ^[0-9]+$ ]]; then
  printf '%s\n' 'No healthy baseline revision found.' >&2
  exit 1
fi

# 정확히 하나의 lifecycle profile 규칙을 의도적으로 위반
kubectl --context kind-gateway-lab set env deployment/backend -n gateway-lab 'SPRING_PROFILES_ACTIVE=local,prod'
if kubectl --context kind-gateway-lab rollout status deployment/backend -n gateway-lab --timeout=60s; then
  printf '%s\n' 'Unexpected success: inspect profile validation.'
else
  printf '%s\n' 'Expected rollout failure: inspect Pods, then run recovery.'
fi
kubectl --context kind-gateway-lab get pods,rs -n gateway-lab -l app=backend
```

이 단계의 rollout timeout은 의도된 결과입니다. 새 Pod가 준비되지 않으면 기존 Pod가
남는지 확인합니다 (`maxUnavailable=0`, `maxSurge=1`). 로그는 실패한 새 Pod 이름을
확인한 후 그 Pod만 조회합니다. 자원 부족도 실패 원인이 될 수 있으므로 Events와 구분합니다.

복구:

```bash
kubectl --context kind-gateway-lab rollout undo deployment/backend -n gateway-lab --to-revision="$backend_revision"
kubectl --context kind-gateway-lab rollout status deployment/backend -n gateway-lab --timeout=180s
```

WSL Bash 터미널을 닫아 revision 변수가 사라졌다면 `rollout history`로 정상 revision을
확인한 뒤 복구합니다. 무작정 최신 revision을 정상이라고 가정하지 않습니다.
체크: [ ] fail-fast 로그 확인 [ ] 정상 revision 복귀 [ ] 인증 API 200 재확인.

## 4-5. 소스 수정 후 새 이미지 배포

코드 수정이 필요할 때의 절차입니다. 수정이 없다면 이 단계는 건너뜁니다.
`dev` 태그를 계속 재사용하기보다 변경마다 새 태그를 붙입니다.

```bash
docker build -t local-backend:lesson-v2 ./backend
if [ "$?" -ne 0 ]; then printf '%s\n' 'Image build failed.' >&2; exit 1; fi
kind load docker-image local-backend:lesson-v2 --name gateway-lab
if [ "$?" -ne 0 ]; then printf '%s\n' 'Image load failed.' >&2; exit 1; fi
kubectl --context kind-gateway-lab set image deployment/backend \
  backend=local-backend:lesson-v2 -n gateway-lab
kubectl --context kind-gateway-lab rollout status deployment/backend -n gateway-lab --timeout=180s
```

`set image`는 클러스터를 바꾸지만 YAML은 바꾸지 않습니다. 유지할 변경이면
`k8s/base/backend.yaml`의 image도 같은 태그로 맞춥니다. 연습을 끝낼 때는 기존
`local-backend:dev`가 kind에 있는지 확인하고 원래 manifest를 적용합니다.
전체 구성을 재적용하면 scale 등 다른 수동 변경도 원래 선언으로 돌아갈 수 있습니다.

## 4-6. 같은 이미지, 다른 overlay

공통 Deployment/Service는 `k8s/base`, 환경별 차이는 `k8s/overlays`에 둡니다.
루트 `k8s/kustomization.yaml`은 local overlay를 가리켜 기존 명령을 유지합니다.

```bash
# 오프라인 렌더링만 수행: 클러스터를 변경하지 않음
kubectl kustomize k8s/overlays/local
kubectl kustomize k8s/overlays/staging
```

비교: namespace `gateway-lab` → `gateway-lab-staging`, profile/environment `staging`,
Backend 복제본 2, Gateway limit CPU 1 / 768Mi, Gateway Service는 ClusterIP입니다.
이미지 이름/태그는 바뀌지 않습니다. 실전에서는 검증된 immutable tag/digest를 승격합니다.

staging은 **렌더링 학습용 템플릿**입니다. 로컬 `/auth/token`은 꺼지며,
새 namespace의 Secret과 신뢰할 토큰 발급자/키를 별도로 준비해야 합니다.
현재 HS256 decoder는 그대로여서 issuer 문자열만 바꾸면 Cognito/JWK 연동이 되는 것은 아닙니다.
staging에 Gateway API 리소스나 외부 진입점을 자동 배포하지 않습니다.
local Secret 생성 스크립트도 staging에는 적용되지 않습니다.

체크: [ ] base와 overlay의 책임 구분 [ ] 동일 이미지 확인 [ ] staging 발급기 비활성화 이해.
[Kustomize 공식 가이드](https://kubernetes.io/docs/tasks/manage-kubernetes-objects/kustomization/)

## 4-7. 후속 장애 격리 실습

[Redis fail-closed / CircuitBreaker / Retry 설계](../resilience-policy.md)를 먼저 읽고,
정책을 선택한 뒤 별도 구현합니다. 현재 route에는 CircuitBreaker/Retry를 추가하지 않았습니다.
연결 거절·응답 지연·HTTP 5xx를 분리해서 재현하고, 쓰기 요청의 중복 실행 위험을 먼저 정합니다.

## 4-8. Spring Boot 설정의 실제 효과 검증

[10단계 기본 구성 검증](10-spring-boot-readiness.md)으로 이어갑니다.
먼저 현재 JWT를 재발급하지 않고 Gateway/Backend Pod 교체 전후 인증을 확인합니다.
정상 rollout과 처리 중 요청의 graceful shutdown은 별도 검증입니다.
느린 요청의 시작/완료를 확인할 fixture는 아직 없으므로 10-3의 구현 조건과 시간 예산부터 준비합니다.
Redis 요청 단위 차단과 프록시 오류 계약은 10-4 및 장애 정책 문서의 후속 구현 기준을 따릅니다.

## 구조·코드 이해 체크

실행 결과를 확인한 뒤 아래 항목을 관련 파일과 연결해 설명합니다. 모든 클래스를 암기하기보다 요청 한 건과 설정 한 개를 끝까지 추적하세요.

**읽을 파일:** [Gateway 배포](../../k8s/base/gateway.yaml) · [Backend 배포](../../k8s/base/backend.yaml) · [ConfigMap](../../k8s/base/configmap.yaml) · [staging overlay](../../k8s/overlays/staging/kustomization.yaml) · [profile guard](../../gateway/src/main/java/com/example/gateway/config/runtime/RuntimeProfileGuard.java) · [후속 장애 정책](../resilience-policy.md)

- [ ] Deployment → ReplicaSet → Pod 관계와 replicas 변경 시 Service가 Ready Pod를 선택하는 과정을 설명한다.
- [ ] ConfigMap 원본, Deployment의 개별 env override, 실행 중 프로세스 환경변수를 구분하고 재기동이 필요한 이유를 설명한다.
- [ ] Redis 장애 시 Gateway readiness와 liveness 차이를 설명하고 EndpointSlice 및 실제 API 결과로 관찰 내용을 뒷받침한다.
- [ ] readiness 제외는 요청마다 Redis 오류를 차단하는 fail-closed 구현과 다르다는 점을 설명한다.
- [ ] `RuntimeProfileGuard`에서 잘못된 profile이 시작 실패로 이어지는 경로를 찾고, Events로 자원 부족과 설정 오류를 구분한다.
- [ ] `maxUnavailable=0/maxSurge=1`, 정상 revision 기록, rollout status/undo를 연결해 실패 배포의 복구 과정을 설명한다.
- [ ] 이미지 빌드 → kind load → image 변경 → rollout 확인을 설명하고, 명령으로 바꾼 상태와 Git YAML 선언의 차이를 기록한다.
- [ ] base/local/staging overlay가 같은 이미지를 다른 설정으로 실행하도록 구성됨을 설명하고, 렌더링 성공을 staging 기동 성공으로 기록하지 않는다.
- [ ] 실습 전후의 복제본·설정·인증 API를 비교해 복구를 확인했다. CircuitBreaker/Retry는 현재 route에 구현되지 않은 후속 과제임을 구분한다.

**학습 기록:** 예상 경로 → 관찰한 HTTP 코드·로그·지표 → 근거 파일 → 복구 결과(해당 시) → 아직 설명하지 못하는 부분을 적습니다. 비밀번호·JWT·Secret 값은 적지 않습니다.

**다음 학습:** 정상 복구 후 [10단계](10-spring-boot-readiness.md)의 기본 검증과 후속 구현 기준을 확인합니다.
이후 [9단계](09-storage-and-persistence.md) 저장소 또는 [5단계](05-gateway-api.md) 외부 진입 경로로 진행합니다.
