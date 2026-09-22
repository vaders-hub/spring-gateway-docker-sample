# 문제 해결, 종료와 다음 학습 재개

[전체 순서](README.md)

## WSL 전환 시 먼저 확인

아래 진단은 기본적으로 **WSL Bash**입니다. 명시된 Windows 확인 블록만 PowerShell입니다.
Windows에 설치한 .exe가 아니라 Linux 도구가 선택되는지 확인합니다.

```bash
type -a docker kind kubectl helm
docker context show
docker version
kind get clusters
kubectl config get-contexts
```

Docker CLI가 WSL Integration 경로에 있는 것은 정상일 수 있습니다. 단, 별도 Linux Docker Engine과
Desktop 엔진을 혼용하거나 `DOCKER_HOST`/`KUBECONFIG`를 이전 환경 값으로 강제하지 않습니다.
kubeconfig 전체 내용에는 인증 정보가 있으므로 출력하지 않습니다.

### Windows에서 이미 만든 kind에 연결

같은 Docker Desktop 엔진의 `gateway-lab`이 존재하지만 WSL에 context가 없을 때만 수행합니다.
기존 kubeconfig를 덮어쓰지 않도록 새 전용 파일을 사용합니다.

```bash
(
  set -euo pipefail
  mkdir -p "$HOME/.kube"
  umask 077
  test ! -e "$HOME/.kube/gateway-lab-wsl" || {
    printf '%s\n' 'Config exists. Inspect its context instead of overwriting.' >&2
    exit 1
  }
  kind export kubeconfig --name gateway-lab --kubeconfig "$HOME/.kube/gateway-lab-wsl"
)
# 위 생성이 성공했거나 이 프로젝트의 기존 파일임을 확인한 뒤 실행
export KUBECONFIG="$HOME/.kube/gateway-lab-wsl"
kubectl --context kind-gateway-lab cluster-info
```

전용 파일을 사용하는 경우 새 WSL 터미널마다 같은 `export KUBECONFIG=...`가 필요합니다.
기존 클러스터를 재생성하거나 Windows kubeconfig를 통째로 복사하지 않습니다.

### localhost / 포트 연결 문제

```bash
ss -ltn
docker ps --format 'table {{.Names}}\t{{.Ports}}'
curl -sS --max-time 5 -o /dev/null -w '%{http_code}\n' http://localhost:8080/actuator/health/readiness
```

Windows 쪽에서만 확인할 때는 **PowerShell**:

```powershell
Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue |
  Where-Object { $_.LocalPort -in 8080,8888,8889,9090,3000 } |
  Select-Object LocalAddress,LocalPort,OwningProcess
curl.exe -sS --max-time 5 -o NUL -w '%{http_code}' http://localhost:8080/actuator/health/readiness
```

- WSL과 Windows에서 모두 실패: 컨테이너/Pod readiness, port-forward 터미널, 실제 포트부터 확인.
- Windows에서만 성공: WSL Integration, WSL/Docker 버전과 NAT/mirrored 네트워크 차이 확인.
- WSL에서만 port-forward 성공: Windows localhost 전달 설정/VPN/방화벽 정책 확인.
- `kubectl cluster-info` 실패: WSL용 kubeconfig와 Docker Desktop 연결부터 확인.
  Windows 전용 context만 있다고 WSL context도 준비된 것은 아닙니다.

Windows 11 22H2 이상에서는 mirrored networking을 선택할 수 있습니다. 필요한 경우에만
사용자 `%USERPROFILE%\.wslconfig`의 기존 설정을 보존하고 아래 항목을 병합합니다.
같은 섹션을 중복 생성하지 않습니다. 모든 환경에 필수인 설정은 아닙니다.

```ini
[wsl2]
networkingMode=mirrored
```

적용에는 WSL 재시작이 필요합니다. `wsl --shutdown`은 **다른 배포판과 Docker 작업까지**
중단시킬 수 있으므로 작업을 저장하고 소유 컨테이너/서비스를 정상 종료한 뒤 별도 유지보수 시점에 합니다.
이 문서 작성 중 전역 설정 변경/재시작은 수행하지 않았습니다.
보안을 낮추는 `--address 0.0.0.0`, 방화벽 전체 해제, 인증서 검증 해제는 해결책으로 사용하지 않습니다.
[WSL 네트워크 공식 문서](https://learn.microsoft.com/en-us/windows/wsl/networking),
[kind WSL2 안내](https://kind.sigs.k8s.io/docs/user/using-wsl2/)

### Bash 스크립트 / 파일 권한

`$'\r': command not found` 또는 `bad interpreter`이면 VS Code에서 해당 `.sh` 파일의
줄바꿈을 LF로 저장합니다. 저장소는 `.gitattributes`에 shell/Wrapper의 LF를 지정했습니다.
`bash scripts/new-local-env.sh`, `bash ./gradlew`처럼 호출하면 실행 비트가 없어도 됩니다.

`.env`는 Bash 코드로 실행하지 않습니다. 새 생성 파일은 Linux에서 umask 077을 사용하지만
`/mnt/c`의 실제 권한은 Windows ACL/DrvFS mount 설정의 영향을 받습니다.
Windows 계정 권한과 화면 공유를 별도로 관리하고 `chmod 777`로 해결하지 않습니다.
Windows의 hiberfil.sys/pagefile.sys 접근 거부는 프로젝트 오류가 아니므로 권한을 바꾸지 않습니다.

## 먼저 관찰하고 좁혀가기

| 증상 | 우선 확인 | 주의 |
|---|---|---|
| Docker Server 접속 실패 | Docker 기동, Linux container mode, WSL2 | 앱 코드를 먼저 바꾸지 않음 |
| 8080 점유 | Compose 또는 기존 kind 노드, 소유 프로세스 | 무관한 프로세스 강제 종료 금지 |
| Gradle/image 다운로드 실패 | 로그의 정확한 태그·저장소·DNS·proxy 오류 | 존재하지 않는 태그인지 먼저 확인; 임의 latest 변경 금지 |
| ImagePullBackOff | Deployment image와 kind load 태그 비교, Events | Docker 호스트 이미지와 kind 이미지 저장소 구분 |
| CrashLoopBackOff | 이전 로그, profile/필수 설정, 종료 코드 | Secret을 전체 출력하지 않음 |
| Pending | Events, requests/limits, 노드 가용 자원 | 작은 PC에서 복제본 증가가 해결책은 아닐 수 있음 |
| Running인데 연결 실패 | Ready, Service selector/port, EndpointSlice | Running ≠ Ready |
| 401 | JWT 만료, issuer/키 불일치, Authorization 전달 | 새 토큰 발급; 인증 비활성화로 우회하지 않음 |
| 429 | subject별 Redis 버킷, 요청 간격 | 오류로 보고 재시도 폭주시키지 않음 |
| 8888 연결 거부 | Envoy port-forward 창/Pod 상태 | Spring NodePort 8080과 다른 경로 |
| 8888에서 404 | HTTPRoute match/status, Actuator 요청 여부 | Actuator 404는 의도된 결과 |
| 변경한 설정이 그대로 | ConfigMap vs Pod 환경변수 vs Deployment override | rollout 필요 여부 확인 |
| Grafana 데이터 없음 | Prometheus Targets, datasource, 조회 시간 범위 | kind에는 관측 스택이 자동 설치되지 않음 |
| NetworkPolicy가 있어도 접근됨 | 실제 CNI 정책 지원/집행, selector | 적용 성공만으로 보안 검증 통과 아님 |

## 기본 진단 명령

```bash
kubectl --context kind-gateway-lab get pods,deploy,svc -n gateway-lab
kubectl --context kind-gateway-lab get events -n gateway-lab --sort-by=.lastTimestamp
kubectl --context kind-gateway-lab describe deployment gateway -n gateway-lab
kubectl --context kind-gateway-lab logs deployment/gateway -n gateway-lab --tail=100
kubectl --context kind-gateway-lab get endpointslices -n gateway-lab
kubectl --context kind-gateway-lab get daemonsets -n kube-system
```

여러 Pod가 있으면 Deployment 로그만으로 실패한 Pod를 놓칠 수 있습니다.
`get pods`에서 실제 이름을 확인한 뒤 `kubectl logs POD_NAME --previous` 형태로
실패 컨테이너 로그를 확인합니다. 항상 로컬 context와 namespace를 붙입니다.
Events/로그를 공유하기 전 민감값 유무를 확인합니다.

Secret 내용의 `get -o yaml`, 전체 `printenv`, 전체 `docker inspect`, 전체 Compose
렌더링은 진단 자료로 공유하지 않습니다. 필요한 비밀값이 아닌 필드만 선택합니다.

## 종료 범위 선택

### Compose만 일시 정지/재개

```bash
docker compose stop
# 다시 사용할 때
docker compose start
```

관측 overlay를 사용했다면 두 compose 파일과 `--profile observability`를 같은 방식으로
붙여 stop/start합니다. 설정/소스 변경을 적용하려면 start가 아니라 해당 구성의 up/rebuild가 필요합니다.

### Compose 제거 (관측 데이터 보존)

```bash
docker compose -f docker-compose.yml -f docker-compose.observability.yml \
  --profile observability down
```

관측 스택을 한 번도 사용하지 않았고 Grafana 비밀값이 없다면 기본 `docker compose down`만
사용합니다. `down -v`는 volume까지 삭제하므로 기본 종료 절차에 넣지 않습니다.

### kind를 유지한 채 학습 앱만 정지/재개

먼저 8888/8889 port-forward 창에서 Ctrl+C로 종료합니다.
아래 방법은 앱 Pod만 없애며 kind/Envoy/system Pod는 계속 자원을 사용합니다.
Redis 데이터는 휘발성이므로 소실됩니다.

```bash
kubectl --context kind-gateway-lab scale deployment/gateway deployment/backend deployment/redis \
  -n gateway-lab --replicas=0
# 다시 사용할 때: 선언된 기본 replicas=1로 복구
kubectl --context kind-gateway-lab apply -k k8s
kubectl --context kind-gateway-lab wait --for=condition=Available \
  deployment/redis deployment/backend deployment/gateway -n gateway-lab --timeout=180s
```

학습 중 유지하려고 변경한 image/replicas/config가 있다면 재적용 전에 YAML에 반영됐는지 확인합니다.
Secret은 남아 있으므로 새로 회전하지 않습니다. Envoy를 유지했다면 새 port-forward를 열면 됩니다.

### 프로젝트 kind 전체 삭제

다시 만들 수 있는 학습 상태인지 확인한 후에만 실행합니다. 이름이 같은 다른 목적의
클러스터가 아닌지 `get clusters`와 로컬 context를 먼저 확인합니다.

```bash
kind get clusters
kubectl --context kind-gateway-lab get namespaces
kind delete cluster --name gateway-lab
kind get clusters
```

이 작업은 클러스터의 Secret/앱/Envoy/로컬 저장 데이터를 제거합니다. Git/YAML로 선언한
리소스와 보관 중인 `.env`를 이용해 재생성할 수 있지만 휘발성 데이터는 복구되지 않습니다.
`.env`, 소스, Docker 호스트의 이미지와 Compose volume은 이 명령의 삭제 대상이 아닙니다.
다음에는 3단계의 생성/이미지 로드/Secret 적용부터, Gateway API는 컨트롤러 설치부터 다시 합니다.

`docker system prune`, 전체 컨테이너 삭제, 전체 Kubernetes namespace 삭제는 사용하지 않습니다.

## 학습 종료 체크

- [ ] 의도한 리소스만 종료했고 다른 프로젝트는 유지했다.
- [ ] 장애 실습용 profile override와 ConfigMap 값은 원상복구했다.
- [ ] 실행한 실습, 관찰한 결과, 아직 미검증인 사항을 기록했다.
- [ ] 다음 기동 시 Compose/kind 중 하나를 선택하고 포트부터 확인한다.

## 구조·코드 이해 체크

실행 결과를 확인한 뒤 아래 항목을 관련 파일과 연결해 설명합니다. 모든 클래스를 암기하기보다 요청 한 건과 설정 한 개를 끝까지 추적하세요.

**읽을 파일:** [Compose](../../docker-compose.yml) · [관측 overlay](../../docker-compose.observability.yml) · [kind](../../k8s/kind-config.yaml) · [Gateway probe](../../k8s/base/gateway.yaml) · [Backend probe](../../k8s/base/backend.yaml)

- [ ] 사용 중인 WSL 도구, Docker 엔진, Kubernetes context/namespace를 확인하고 장애가 발생한 환경을 특정할 수 있다.
- [ ] 진입 포트 → Service/EndpointSlice → Pod Ready → 앱 로그 순서로 연결 경로를 좁히며, 각 확인이 어떤 가설을 검증하는지 설명한다.
- [ ] ImagePullBackOff·CrashLoopBackOff·Pending·401·429를 구분하고 해당 Events/로그/설정 파일을 연결한다.
- [ ] Windows와 WSL의 localhost 차이, port-forward 프로세스, Compose/kind의 8080 충돌을 구분한다.
- [ ] `stop/start`, `down`, `scale=0`, kind 삭제의 범위 및 volume/Secret/휘발성 Redis 데이터에 미치는 영향을 설명한다.
- [ ] 설정·소스 변경 적용과 단순 재개를 구분하고, 다음 실행에 필요한 이미지·환경파일·Secret·토큰 재발급 순서를 기록했다.
- [ ] 실습에서 바꾼 값과 복구 결과, 남아 있는 리소스 및 미검증 사항을 기록했으며 로그에 비밀값을 남기지 않았다.

**학습 기록:** 예상 경로 → 관찰한 HTTP 코드·로그·지표 → 근거 파일 → 복구 결과(해당 시) → 아직 설명하지 못하는 부분을 적습니다. 비밀번호·JWT·Secret 값은 적지 않습니다.

**다음 학습:** 이 문서는 전체 실습에서 필요할 때 참조합니다. 재개할 단계의 정상 상태를 먼저 확인한 후 진행합니다.
