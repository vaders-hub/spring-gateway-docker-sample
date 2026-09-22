# 무료 로컬 AWS/EKS 학습 로드맵

공식 문서 확인일: 2026-09-22. 대상: Windows VS Code·Docker Desktop + WSL2 Linux Bash, 현재 저장소의 Spring Boot 샘플.

## 이번 세션의 목표와 무료의 의미

**AWS 리소스를 생성하지 않고 로컬 Kubernetes에서 EKS에 공통으로 적용되는 개념을 익힙니다.**
동시에 Spring Boot의 설정·보안·계층 분리·운영 기본선을 확장합니다.
kind는 로컬 Kubernetes이지 AWS EKS 에뮬레이터가 아닙니다. IAM, VPC, ALB, EBS,
관리형 control plane까지 로컬에서 재현된다고 가정하지 않습니다.

이 과정은 AWS 계정/카드/Access Key/`aws configure`/`eksctl`이 필요하지 않습니다.
로컬 PC 자원과 인터넷 다운로드는 사용합니다. Docker Desktop의 개인 학습용 무료 조건과
회사 사용 시 라이선스는 [공식 설치 안내](https://docs.docker.com/desktop/setup/install/windows-install/)에서
확인하세요. 회사 정책상 사용할 수 없다면 승인된 Linux 컨테이너 환경을 먼저 준비합니다.

실제 EKS에는 클러스터와 노드 등 별도 요금이 있습니다. Free Tier/크레딧을 근거로
무과금을 보장하지 않습니다. 무료 학습 경로에서는 EKS/EC2/NAT Gateway/ALB/RDS를
만들거나 AWS를 대상으로 Terraform을 적용하지 않습니다.
[EKS 공식 요금](https://aws.amazon.com/eks/pricing/)

## 학습 순서

| 단계 | 문서 | 직접 할 일 | 완료 기준 |
|---|---|---|---|
| 설치 | [Windows + WSL2 기본 도구 설치](00-windows-tool-installation.md) | WSL2·Docker·kubectl·kind·Helm, 선택 JDK 25 | 버전/PATH 및 Docker Server 확인 |
| 0~1 | [준비와 Compose](01-setup-and-compose.md) | 도구 확인, 환경변수, JWT, API, Redis 제한 | 200/400/401/429를 구분 |
| 2 | [관측성](02-observability.md) | Prometheus/Grafana 기동, 요청 지표 확인 | 두 scrape target의 up=1 |
| 3 | [kind 배포](03-kind-kubernetes.md) | 이미지 로드, Namespace/Secret/Deployment/Service | 3개 앱 Deployment Available |
| 4 | [운영·장애 실습](04-operations-and-recovery.md) | 복제본, ConfigMap, readiness, rollout/복구 | 장애를 관찰하고 원상복구 |
| 5 | [Gateway API](05-gateway-api.md) | Envoy + Gateway/HTTPRoute 추가 | 8888 경로에서도 인증·제한 유지 |
| 6 | [AWS 대응과 Spring 확장](06-aws-and-spring-expansion.md) | EKS 차이 정리, Oracle/JPA/MyBatis 설계 | 로컬 검증/미검증 경계 설명 |
| 상시 | [문제 해결·종료](07-troubleshooting-and-cleanup.md) | 증거 기반 진단과 프로젝트만 종료 | 다른 프로젝트에 영향 없음 |
| 3단계 보충 | [NetworkPolicy 검증](08-network-policy-validation.md) | 허용 경로·차단 경로 대조 | 집행/미집행/판정 불가 구분 |
| 9 (3~4단계 후속) | [PV·PVC와 저장·복원](09-storage-and-persistence.md) | 전용 namespace에서 Pod 교체, PVC 삭제, 호스트 백업·새 PVC 복원 | emptyDir/PVC 수명 차이와 복원 파일 checksum 일치 |
| 4단계 후속 | [장애 정책 설계](../resilience-policy.md) | fail-closed·CircuitBreaker·Retry 설계 | 기본 동작과 미구현 과제 구분 |

1~2단계는 Compose, 3~5단계는 kind입니다. **Compose와 kind를 기본 설정으로 동시에
띄우면 8080 포트가 충돌합니다.** 2단계 종료 후 Compose를 내리고 kind로 넘어갑니다.
모든 단계를 한꺼번에 띄울 필요는 없습니다. Oracle과 tracing backend는 후속 선택 과제입니다.
권장 순서는 `01 → 02 → 03 → 08(보충) → 04 → 09(저장소) → 05 → 06`입니다.
09는 기존 kind를 사용하는 독립 저장소 실습이며 Gateway API 설치는 필요하지 않습니다. 07은 상시 참고합니다.

## 현재 준비된 것과 앞으로 구현할 것

| 상태 | 내용 |
|---|---|
| 코드/설정 존재 | Java 25·Gradle, JWT·필터·Redis 제한, Controller/Service/DTO, 프로필·검증 |
| 매니페스트 존재 | Compose, kind 기본 리소스, 선택형 Envoy/Gateway API |
| Compose 관측 설정 존재 | Prometheus scrape, Grafana datasource; 완성 dashboard는 없음 |
| 계측 설정만 존재 | OpenTelemetry; Collector/trace 저장소는 없고 export는 기본 off |
| 추가 구현 필요 | Oracle 연결, JPA/MyBatis, migration, 업무 Entity/Repository/Mapper |
| 보안 보완 과제 | Redis 오류 시 요청 단위 fail-closed; 현재 readiness 제외와 별개 |
| 저장소 실습 문서 존재 | 09에 PVC/Deployment YAML과 백업·복원 절차 포함; 실행 시에만 전용 리소스 생성 |
| 별도 실습 필요 | CNI 정책 집행, 09의 로컬 저장·복원 실제 확인, metrics-server/HPA |
| 이 무료 과정에서 실행하지 않음 | AWS 리소스 생성, 실제 EKS의 IAM/VPC/ALB 검증 |

이번 문서 작성은 기동·컴파일·테스트를 수행했다는 의미가 아닙니다.
각 문서의 명령은 학습자가 실습 시 실행할 절차이고 체크박스는 직접 확인한 뒤 표시합니다.
Docker 이미지 빌드는 Java 컴파일을 포함하므로 시간이 준비됐을 때 실행합니다.

## 공통 실행 규칙

초기 Windows 설정 이외의 코드 블록은 **WSL Bash**입니다. WSL 아이콘은 기본 배포판을 엽니다.
VS Code 화면과 Docker Desktop은 Windows에 두고, VS Code는 WSL 연결로 해당 폴더를 엽니다.
소스를 실제로 이동하지 않았으므로 현재 경로는 Windows 파일의 WSL 마운트 경로입니다.

```bash
cd /mnt/c/dev/personal/infra/spring-gateway-docker-sample
set -o pipefail
```

- 모든 상대 경로는 위 저장소 루트 기준입니다. 문서 폴더에서 실행하지 않습니다.
- 명령 하나가 실패하면 다음 단계로 넘어가지 않습니다. 실패 종료 코드를 무시하지 않습니다. Bash 파이프는 `set -o pipefail`로 앞 단계 실패도 감지합니다.
- Kubernetes 명령에는 `--context kind-gateway-lab`과 대상 namespace를 명시합니다.
- `.env` 내용, JWT, Secret YAML, 전체 환경변수 출력은 로그/화면 공유에 남기지 않습니다.
  `docker compose config`는 값이 펼쳐질 수 있으므로 검증에는 `config --quiet`를 사용합니다.
- 장애 실습은 이 프로젝트의 로컬 리소스만 대상으로 합니다. 복구 명령부터 읽습니다.
- 새 WSL Bash 터미널에서는 `source scripts/local-api.sh`와 `lab_login`을 다시 실행합니다.
- 구버전 이미지/도구를 임의 조합하지 않습니다. 설치일에 공식 호환표와 보안 공지를 재확인합니다.

## 학습 기록 양식

각 단계가 끝나면 개인 노트에 아래 내용을 기록합니다. 비밀값은 기록하지 않습니다.

```text
단계 / 날짜:
도구·이미지 버전 / context:
변경한 파일 또는 명령:
예상 결과:
관찰한 상태·HTTP 코드·requestId:
실패 원인과 근거:
복구 결과:
아직 검증하지 않은 것:
```

목표는 명령 복사가 아니라 "요청이 어떤 계층을 통과하고, 장애 때 무엇이 바뀌며,
이 구성이 EKS에서 어느 AWS 기능과 연결되는가"를 설명할 수 있게 되는 것입니다.
