# 9단계: PV·PVC와 로컬 데이터 보존·백업·복원

[전체 순서](README.md) · 선수: [3단계 kind](03-kind-kubernetes.md) · 관련: [4단계 운영](04-operations-and-recovery.md) / [6단계 확장](06-aws-and-spring-expansion.md)

## 목표와 실습 범위

Docker 볼륨에서 배운 데이터 수명을 Kubernetes의 PV·PVC·StorageClass로 연결합니다.
3단계의 kind가 정상인 상태에서 진행하며, 처음 학습할 때는 4단계까지 마친 뒤 진행하면 좋습니다.
Gateway API나 NetworkPolicy 실습의 성공은 선수 조건이 아닙니다.

기존 `gateway-lab` 앱 대신 **새 namespace `gateway-storage-lab`**에 BusyBox 한 개와 PVC를 만듭니다.
Gateway/Backend/Redis의 설정은 바꾸지 않습니다. DB/JPA나 Redis AOF 도입도 이 단계의 범위가 아닙니다.
포트를 공개하지 않으며 `kubectl exec`로 작은 샘플 텍스트만 읽고 씁니다.

이 문서는 실행 절차입니다. 문서 작성만으로 현재 PC의 저장·복원이 검증된 것은 아닙니다.
AWS/Azure 자원은 생성하지 않으며 kind 노드 자체의 삭제·재생성도 수행하지 않습니다.

| 저장 방식 | 이 과정에서 확인할 데이터 수명 |
|---|---|
| Compose named volume | 컨테이너 제거와 volume 제거가 별개: 2단계 관측 데이터 |
| `emptyDir` | 같은 Pod 안의 컨테이너 재시작에는 유지되지만 Pod 삭제/교체 시 소실 |
| PVC에 연결한 PV | Pod가 교체되어도 동일 PVC/PV에 저장한 데이터 유지 |
| 호스트에 저장한 백업 파일 | PVC를 지운 뒤 새 PVC로 복원할 때 사용하는 별도 사본 |

```text
Deployment → Pod → volumeMount /data → PVC → PV → kind 노드의 로컬 디렉터리
                                      ↑
                        StorageClass + provisioner가 동적 생성/바인딩

Pod의 /scratch → emptyDir           WSL의 백업 디렉터리 ← tar 스트림 ← /data
```

PVC는 namespace에 속하는 저장 공간 요청이고 PV는 클러스터 범위의 저장 자원입니다.
StorageClass는 provisioner·바인딩 시점·회수 정책을 선택합니다.
아래에서는 기존 class를 사용하며 PV를 직접 작성하지 않습니다.
`ReadWriteOnce`는 한 노드에서 읽고 쓰는 모드이지 한 Pod만 접근하도록 잠그는 장치가 아닙니다.
[공식 PV 설명](https://kubernetes.io/docs/concepts/storage/persistent-volumes/)

## 9-1. StorageClass와 provisioner 확인

아래 블록은 모두 **동일한 WSL Bash 터미널**에서 순서대로 실행합니다. 실패 시 다음으로 넘어가지 않습니다.
새 터미널로 바뀌면 이전 실습 디렉터리와 리소스부터 확인하며 생성 절차를 무작정 반복하지 않습니다.

```bash
cd /mnt/c/dev/personal/infra/spring-gateway-docker-sample
set -euo pipefail
kubectl --context kind-gateway-lab get nodes
kubectl --context kind-gateway-lab get storageclass
kubectl --context kind-gateway-lab get deployment,pods -n local-path-storage
kubectl --context kind-gateway-lab get storageclass standard -o yaml

# 이 절차는 아래 조합만 대상으로 한다. 다르면 중단하고 실제 class/구현을 먼저 확인한다.
test "$(kubectl --context kind-gateway-lab get storageclass standard -o jsonpath='{.provisioner}')" = rancher.io/local-path
test "$(kubectl --context kind-gateway-lab get storageclass standard -o jsonpath='{.volumeBindingMode}')" = WaitForFirstConsumer
test "$(kubectl --context kind-gateway-lab get storageclass standard -o jsonpath='{.reclaimPolicy}')" = Delete
kubectl --context kind-gateway-lab rollout status deployment/local-path-provisioner \
  -n local-path-storage --timeout=90s
```

`standard`라는 이름만으로 동작을 가정하지 않습니다. class나 deployment가 없거나 위 조건과 다르면
이 문서의 생성/삭제 단계는 실행하지 않습니다. 다른 provisioner를 즉석 설치하거나 기본 class를 바꾸지 않습니다.

- `WaitForFirstConsumer`: PVC만 있을 때 Pending일 수 있습니다. 사용할 Pod가 스케줄링되면서 PV가 생성·연결됩니다.
- `Delete`: PVC 해제 후 provisioner가 PV와 실제 저장 경로를 정리하는 정책입니다. Pod 삭제와 다릅니다.
- 이 local-path 구성의 디스크는 kind 노드 컨테이너에 종속됩니다. PVC가 있어도 노드/클러스터 삭제를 견디는 백업은 아닙니다.
- 아래 `64Mi`는 저장 공간 요청입니다. local-path의 실제 용량 제한이 강제된다고 가정하지 말고 작은 파일만 사용합니다.

근거: [StorageClass](https://kubernetes.io/docs/concepts/storage/storage-classes/),
[Local Path Provisioner의 동작·제약](https://github.com/rancher/local-path-provisioner).

## 9-2. 전용 namespace와 매니페스트 준비

같은 이름의 namespace가 이미 있다면 `create`가 실패합니다. 다른 실습 데이터와 섞이지 않도록
소유·내용·백업부터 확인하고 중단된 단계에서 재개하세요. 기존 namespace를 지워서 진행하지 않습니다.

```bash
kubectl --context kind-gateway-lab create namespace gateway-storage-lab
kubectl --context kind-gateway-lab label namespace gateway-storage-lab learning-stage=storage-09

# 저장소 밖 WSL 홈에 생성한다. 실제 데이터/백업을 Git에 추가하지 않는다.
umask 077
storage_lab_dir=$(mktemp -d "$HOME/gateway-storage-lab.XXXXXX")
printf '실습/백업 디렉터리: %s\n' "$storage_lab_dir"
cat > "$storage_lab_dir/lab.yaml" <<'YAML'
# PVC는 Pod와 수명이 다르다. 아래 Deployment가 재생성되어도 이 claim을 재사용한다.
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: sample-data
  namespace: gateway-storage-lab
spec:
  storageClassName: standard
  accessModes: [ReadWriteOnce]
  resources:
    requests:
      storage: 64Mi
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: storage-writer
  namespace: gateway-storage-lab
spec:
  replicas: 1
  # 교체 중 두 writer가 동시에 실행되지 않도록 학습용 단일 writer로 유지한다.
  strategy:
    type: Recreate
  selector:
    matchLabels:
      app: storage-writer
  template:
    metadata:
      labels:
        app: storage-writer
    spec:
      automountServiceAccountToken: false
      terminationGracePeriodSeconds: 5
      securityContext:
        runAsNonRoot: true
        runAsUser: 10001
        runAsGroup: 10001
        fsGroup: 10001
        seccompProfile:
          type: RuntimeDefault
      containers:
        - name: writer
          image: busybox:1.37.0
          # 자동으로 파일을 만들지 않는다. 재생성 후 데이터 보존 여부를 직접 판별한다.
          command: ["sh", "-c", "while true; do sleep 3600; done"]
          resources:
            requests:
              cpu: 10m
              memory: 16Mi
            limits:
              cpu: 100m
              memory: 64Mi
          securityContext:
            allowPrivilegeEscalation: false
            readOnlyRootFilesystem: true
            capabilities:
              drop: [ALL]
          volumeMounts:
            - name: persistent-data
              mountPath: /data
            - name: temporary-data
              mountPath: /scratch
      volumes:
        - name: persistent-data
          persistentVolumeClaim:
            claimName: sample-data
        - name: temporary-data
          emptyDir: {}
YAML

kubectl --context kind-gateway-lab apply -f "$storage_lab_dir/lab.yaml"
# PVC Bound만 먼저 기다리면 WaitForFirstConsumer에서 막힐 수 있으므로 Pod도 함께 생성한다.
kubectl --context kind-gateway-lab rollout status deployment/storage-writer \
  -n gateway-storage-lab --timeout=180s
kubectl --context kind-gateway-lab get pod,pvc -n gateway-storage-lab
storage_pv=$(kubectl --context kind-gateway-lab get pvc sample-data -n gateway-storage-lab -o jsonpath='{.spec.volumeName}')
test -n "$storage_pv"
kubectl --context kind-gateway-lab get pv "$storage_pv"
```

완료 기준은 Pod Ready와 PVC/PV Bound입니다. 이 Pod에는 업무 readiness probe가 없으므로
Ready만으로 쓰기 가능을 증명하지 않습니다. 다음 단계에서 파일 쓰기로 확인합니다.
Pending이면 PVC/Pod의 `describe`와 namespace Events, provisioner 로그를 확인합니다.
권한 오류가 나면 volume 소유권과 provisioner 설정을 확인하고 광범위한 `chmod 777`로 우회하지 않습니다.

## 9-3. Pod 교체 전후의 데이터 비교

```bash
kubectl --context kind-gateway-lab exec -n gateway-storage-lab deployment/storage-writer -- \
  sh -ec 'printf "persistent sample 09\n" > /data/sample.txt; printf "temporary sample\n" > /scratch/sample.txt; sync'
storage_old_uid=$(kubectl --context kind-gateway-lab get pod -n gateway-storage-lab \
  -l app=storage-writer -o jsonpath='{.items[0].metadata.uid}')

# 여기서 삭제하는 것은 실습 Pod뿐이다. PVC는 유지되고 Deployment가 새 Pod를 만든다.
kubectl --context kind-gateway-lab delete pod -n gateway-storage-lab -l app=storage-writer --wait=true
kubectl --context kind-gateway-lab rollout status deployment/storage-writer -n gateway-storage-lab --timeout=180s
storage_new_uid=$(kubectl --context kind-gateway-lab get pod -n gateway-storage-lab \
  -l app=storage-writer -o jsonpath='{.items[0].metadata.uid}')
test "$storage_old_uid" != "$storage_new_uid"
kubectl --context kind-gateway-lab exec -n gateway-storage-lab deployment/storage-writer -- \
  sh -c 'cat /data/sample.txt; test ! -e /scratch/sample.txt'
test "$(kubectl --context kind-gateway-lab get pvc sample-data -n gateway-storage-lab -o jsonpath='{.spec.volumeName}')" = "$storage_pv"
```

기대 결과: Pod UID는 바뀌고 PV 이름은 같으며 `/data/sample.txt`만 남습니다.
파일이 없거나 임시 파일까지 남았다면 실제 Pod 교체 여부와 mount를 확인하고 다음 단계로 넘어가지 않습니다.
이 결과는 **Pod 교체 시 보존**이며 노드 장애·클러스터 삭제·다중 AZ 복구를 검증한 것은 아닙니다.

## 9-4. WSL 호스트에 백업

이 Pod는 백그라운드에서 파일을 수정하지 않습니다. 쓰기가 멈춘 샘플 파일만 백업합니다.
실제 DB의 데이터 디렉터리를 실행 중 tar로 복사하는 것은 일관된 DB 백업을 보장하지 않습니다.

```bash
test -d "$storage_lab_dir"
kubectl --context kind-gateway-lab exec -n gateway-storage-lab deployment/storage-writer -- \
  sha256sum /data/sample.txt > "$storage_lab_dir/before.sha256"
kubectl --context kind-gateway-lab exec -n gateway-storage-lab deployment/storage-writer -- \
  tar -C /data -cf - sample.txt > "$storage_lab_dir/sample.tar.part"
test -s "$storage_lab_dir/sample.tar.part"
tar -tf "$storage_lab_dir/sample.tar.part"
mv "$storage_lab_dir/sample.tar.part" "$storage_lab_dir/sample.tar"
sha256sum "$storage_lab_dir/sample.tar" > "$storage_lab_dir/archive.sha256"
sha256sum -c "$storage_lab_dir/archive.sha256"
```

백업 위치는 Pod/PVC 밖 WSL 파일시스템입니다. tar 목록에 `sample.txt`가 있고 checksum 검사가
성공해야 다음 삭제 실습으로 넘어갑니다. `kubectl exec`에 `-t`를 붙이면 바이너리 스트림이
변형될 수 있으므로 사용하지 않습니다. 백업은 로컬 사본이며 WSL/PC 손실까지 보호하지 않습니다.

## 9-5. Delete 정책과 새 PVC 복원

**이 절차는 방금 만든 샘플 PVC와 파일을 실제로 삭제합니다.** 기존 앱 namespace/PVC에 적용하지 않습니다.
검증한 백업이 있을 때만 실행하며, 정책이 Retain이거나 PVC/PV 참조가 다르면 중단합니다.

```bash
sha256sum -c "$storage_lab_dir/archive.sha256"
test "$(kubectl --context kind-gateway-lab get namespace gateway-storage-lab -o jsonpath='{.metadata.labels.learning-stage}')" = storage-09
test "$(kubectl --context kind-gateway-lab get pv "$storage_pv" -o jsonpath='{.spec.claimRef.namespace}/{.spec.claimRef.name}')" = gateway-storage-lab/sample-data
test "$(kubectl --context kind-gateway-lab get pv "$storage_pv" -o jsonpath='{.spec.persistentVolumeReclaimPolicy}')" = Delete

# PVC를 사용하는 Pod를 먼저 없앤다. 사용 중 PVC 보호 finalizer를 강제 제거하지 않는다.
kubectl --context kind-gateway-lab scale deployment/storage-writer -n gateway-storage-lab --replicas=0
kubectl --context kind-gateway-lab delete pod -n gateway-storage-lab -l app=storage-writer --ignore-not-found --wait=true --timeout=90s
kubectl --context kind-gateway-lab delete pvc sample-data -n gateway-storage-lab --wait=true --timeout=90s
kubectl --context kind-gateway-lab wait --for=delete "pv/$storage_pv" --timeout=120s

# 같은 PVC 이름이어도 새 객체/새 PV이다. YAML의 replicas=1도 다시 적용한다.
kubectl --context kind-gateway-lab apply -f "$storage_lab_dir/lab.yaml"
kubectl --context kind-gateway-lab rollout status deployment/storage-writer -n gateway-storage-lab --timeout=180s
storage_restored_pv=$(kubectl --context kind-gateway-lab get pvc sample-data -n gateway-storage-lab -o jsonpath='{.spec.volumeName}')
test -n "$storage_restored_pv"
test "$storage_pv" != "$storage_restored_pv"
kubectl --context kind-gateway-lab exec -n gateway-storage-lab deployment/storage-writer -- test ! -e /data/sample.txt

# 새 볼륨이 비어 있음을 확인한 후 백업을 stdin으로 전달해 복원한다.
kubectl --context kind-gateway-lab exec -i -n gateway-storage-lab deployment/storage-writer -- \
  tar -C /data -xf - < "$storage_lab_dir/sample.tar"
kubectl --context kind-gateway-lab exec -n gateway-storage-lab deployment/storage-writer -- \
  sha256sum /data/sample.txt > "$storage_lab_dir/after.sha256"
diff "$storage_lab_dir/before.sha256" "$storage_lab_dir/after.sha256"
```

`diff` 출력 없이 종료 코드 0이면 새 PV의 파일이 백업 전 파일과 같습니다.
PV 삭제 timeout이면 Events와 local-path-provisioner 로그부터 확인하고 finalizer를 강제로 지우지 않습니다.
API에서 PV가 사라졌다는 것만으로 디스크의 보안 삭제까지 증명한 것은 아닙니다.

| 정책 | PVC 해제 후 의미 | 이 단계의 범위 |
|---|---|---|
| Delete | 지원 provisioner가 PV/저장 자원을 정리 | 샘플 PVC 삭제 → 새 PV 생성 → 백업 복원 |
| Retain | PV/데이터를 남기고 관리자가 회수·재사용 절차를 처리 | 개념 비교만 수행; 자동 재연결/자동 백업이 아님 |

StorageClass 정책을 바꿔도 이미 생성된 PV의 정책이 자동으로 바뀌는 것은 아닙니다.
이 실습은 공용 StorageClass나 기존 PV의 정책을 변경하지 않습니다.
[공식 회수 정책 변경 설명](https://kubernetes.io/docs/tasks/administer-cluster/change-pv-reclaim-policy/)

## 9-6. 종료와 재개

계속 보관하려면 Deployment만 `replicas=0`으로 내리고 PVC는 유지합니다.
재개할 때 실습 디렉터리 경로를 다시 설정하고 `lab.yaml`을 적용하면 됩니다.
처음부터 `create namespace`를 다시 실행하지 않습니다. 현재 PVC/PV를 확인한 뒤 해당 단계부터 이어갑니다.

완전히 정리할 때는 아래 명령으로 **9단계 전용 리소스만** 삭제합니다. 복원 결과와 백업 위치를 먼저 기록하세요.
namespace에 다른 리소스를 추가했다면 이 일괄 정리 절차를 사용하지 않습니다.

```bash
test "$(kubectl --context kind-gateway-lab get namespace gateway-storage-lab -o jsonpath='{.metadata.labels.learning-stage}')" = storage-09
kubectl --context kind-gateway-lab get deploy,pod,pvc -n gateway-storage-lab
storage_cleanup_pv=$(kubectl --context kind-gateway-lab get pvc sample-data -n gateway-storage-lab -o jsonpath='{.spec.volumeName}')
test -n "$storage_cleanup_pv"
test "$(kubectl --context kind-gateway-lab get pv "$storage_cleanup_pv" -o jsonpath='{.spec.persistentVolumeReclaimPolicy}')" = Delete
kubectl --context kind-gateway-lab delete deployment storage-writer -n gateway-storage-lab --wait=true
kubectl --context kind-gateway-lab delete pod -n gateway-storage-lab -l app=storage-writer --ignore-not-found --wait=true --timeout=90s
kubectl --context kind-gateway-lab delete pvc sample-data -n gateway-storage-lab --wait=true --timeout=90s
kubectl --context kind-gateway-lab wait --for=delete "pv/$storage_cleanup_pv" --timeout=120s
kubectl --context kind-gateway-lab delete namespace gateway-storage-lab --wait=true --timeout=90s
```

공용 `standard` StorageClass, provisioner, 기존 `gateway-lab`, kind 클러스터는 남깁니다.
호스트의 `storage_lab_dir`에는 백업과 YAML을 보존합니다. 나중에 필요 없음을 확인한 뒤 해당 디렉터리만 직접 정리합니다.

## 구조·코드 이해 체크

**읽을 파일:** [Compose 관측 볼륨](../../docker-compose.observability.yml) ·
[Redis emptyDir](../../k8s/base/redis.yaml) · [kind 노드 설정](../../k8s/kind-config.yaml) ·
[AWS 대응](06-aws-and-spring-expansion.md) · [업무 DB 도입 기준](../persistence-policy.md)

- [ ] Deployment → Pod volumeMount → PVC → PV → 실제 로컬 경로의 관계를 설명한다.
- [ ] namespace 범위의 PVC와 클러스터 범위의 PV/StorageClass를 구분한다.
- [ ] StorageClass의 provisioner, WaitForFirstConsumer, Delete 정책을 실제 값으로 확인했다.
- [ ] Pod UID 변경·동일 PV·영속 파일 유지·emptyDir 파일 소실을 함께 기록했다.
- [ ] RWO를 단일 Pod 접근 제한이나 데이터 복제/고가용성 보장으로 오해하지 않는다.
- [ ] Pod 삭제, PVC 삭제, kind 노드/클러스터 삭제, WSL/PC 손실의 차이를 설명한다.
- [ ] PVC 삭제 전 호스트 백업을 검증하고 새 PV로 복원한 파일의 checksum 일치를 확인했다.
- [ ] Delete와 Retain을 설명하고 Retain이 자동 백업·자동 재바인딩은 아님을 이해했다.
- [ ] DB 백업에는 일관성 보장이 별도로 필요하며 이 샘플 tar 절차를 그대로 운영 DB에 적용하지 않는다.
- [ ] 실습 namespace/PVC만 정리했고 공용 provisioner와 기존 앱은 유지했다.

**학습 기록:** StorageClass 값 / 교체 전후 Pod UID·PV 이름 / 임시·영속 파일 결과 /
백업 위치·checksum / 복원 후 checksum / 정리 결과 / 미검증 사항을 남깁니다.

**다음 학습:** 6단계의 영속성 확장 계획과 연결합니다. EBS/EFS 등의 실제 CSI 구성,
AZ 제약·노드 장애·스냅샷 복원 검증은 별도 클라우드 과제이며 이번 로컬 성공으로 대체하지 않습니다.
