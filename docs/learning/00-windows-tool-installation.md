# Windows + WSL2 기본 도구 설치 가이드

[전체 학습 순서](README.md) · 다음: [환경 준비와 Compose](01-setup-and-compose.md)

공식 문서 확인일: 2026-09-22. **Windows에서 편집기와 Docker Desktop을 실행하고,
실습 명령은 WSL2의 Linux Bash에서 실행**합니다. 기존 링크 유지를 위해 파일명은 유지합니다.
이 문서의 설치·설정·기동 명령을 작성 과정에서 실행하지 않았습니다.

## 1. WSL 아이콘과 Linux 배포판의 관계

시작 메뉴의 WSL 아이콘 또는 PowerShell의 `wsl`은 **기본 Linux 배포판을 여는 진입점**입니다.
기본 배포판이 Ubuntu이면 WSL 아이콘과 Ubuntu 아이콘은 같은 Linux 환경으로 들어갑니다.
Ubuntu 앱을 따로 클릭할 필요는 없지만 Linux 배포판 자체는 필요합니다.
Docker 내부용 `docker-desktop` 배포판은 개발 터미널로 사용하지 않습니다.
[Microsoft WSL 명령](https://learn.microsoft.com/en-us/windows/wsl/basic-commands)

| 영역 | 설치/실행 위치 | 도구 |
|---|---|---|
| OS 설정 | Windows PowerShell | WSL2, WinGet |
| 편집기 화면 | Windows | VS Code + WSL 확장 |
| 컨테이너 엔진 | Windows Docker Desktop의 WSL2 backend | Docker·Compose, 대상 배포판 WSL Integration |
| 명령어·Java 확장 실행 | WSL2 Linux | Bash, curl, jq, OpenSSL, kind, kubectl, Helm, 선택 JDK 25 |
| 빌드 도구 | WSL2에서 저장소 Wrapper 실행 | `bash ./gradlew`; 전역 Gradle 설치 불필요 |

Windows에 WinGet으로 설치한 kind/Helm/JDK는 Linux용 설치와 별개입니다.
기존 Windows 도구를 지우지는 않지만 아래 실습에서는 Linux 실행 파일을 사용합니다.
AWS CLI/eksctl/계정/결제 정보는 필요하지 않습니다. Redis·Prometheus·Grafana도 호스트에 직접 설치하지 않습니다.

## 2. Windows PowerShell에서 하는 초기 준비

이 절의 코드 블록만 **Windows PowerShell**입니다.

```powershell
wsl --list --verbose
wsl --status
wsl --version
```

목록의 `*`가 기본 배포판이며 개발용 배포판의 VERSION이 `2`인지 확인합니다.
이미 설치되어 있으면 재설치하지 않습니다. 기본 배포판 변경이 필요한 경우에만,
아래 이름을 실제 목록의 이름(예: Ubuntu-24.04)으로 바꿔 실행합니다.

```powershell
wsl --set-default Ubuntu
wsl
```

WSL이 전혀 없을 때만 관리자 PowerShell에서 `wsl --install`을 실행하고 재부팅합니다.
기본 Ubuntu 설치와 사용자 생성까지 완료합니다. 업데이트는 `wsl --update`입니다.
VERSION이 1이면 기존 데이터를 백업한 후 공식 절차로 해당 배포판을 2로 전환합니다.
[WSL 설치](https://learn.microsoft.com/en-us/windows/wsl/install)

Windows 도구가 없는 경우에만 설치합니다.

```powershell
winget --version
winget install --id Docker.DockerDesktop --exact --source winget
winget install --id Microsoft.VisualStudioCode --exact --source winget
```

WinGet이 없으면 Microsoft Store의 Microsoft **앱 설치 관리자(App Installer)**를 설치/업데이트합니다.
[WinGet 안내](https://learn.microsoft.com/en-us/windows/package-manager/winget/)

Docker Desktop을 시작하고 다음을 확인합니다.

1. Linux containers 모드, Settings → General → Use WSL 2 based engine.
2. Settings → Resources → WSL Integration에서 **실제 사용할 개발용 배포판**을 켜고 적용.
3. Docker Desktop 자체 Kubernetes는 꺼둡니다. kind로 별도 학습합니다.
4. Linux 안에 Docker Engine을 중복 설치하지 않습니다. 이미 별도 엔진이 있다면
   데이터/컨테이너 소유권부터 확인하고 Docker Desktop 연결과 구분합니다.

개인 학습과 회사 사용의 라이선스 조건 및 Windows/WSL 지원 버전을 확인하세요.
[Docker Windows 설치](https://docs.docker.com/desktop/setup/install/windows-install/),
[Docker WSL 연동](https://docs.docker.com/desktop/features/wsl/)

## 3. 이후 명령은 WSL Bash에서 실행

WSL을 열고 확인합니다. 아래 패키지 설치는 **Ubuntu/Debian 계열** 기준입니다.
다른 배포판이면 해당 패키지 관리자를 사용하세요.

```bash
cat /etc/os-release
uname -m
printf '%s\n' "$SHELL"
sudo apt-get update
sudo apt-get install -y ca-certificates curl jq openssl tar gzip unzip
docker version
docker info --format '{{.OSType}}'
docker compose version
```

Docker Client·Server가 모두 확인되고 OSType이 `linux`여야 합니다.
Compose는 Docker Desktop에 포함되므로 별도 설치하지 않습니다.
명령이 없거나 Server 연결이 실패하면 Windows Docker Desktop과 WSL Integration부터 확인합니다.
[Compose 설치](https://docs.docker.com/compose/install/)

사용자 도구 경로를 준비합니다.

```bash
mkdir -p "$HOME/.local/bin"
export PATH="$HOME/.local/bin:$PATH"
```

새 터미널에서도 유지하려면 편집기로 `~/.bashrc`에 위 `export PATH=...` 줄을 **한 번만** 추가합니다.
이후 `source ~/.bashrc` 또는 새 WSL 터미널을 사용합니다. 아래 설치 블록은 괄호로 감싼
별도 Bash 프로세스에서 실행되며 실패하면 그 블록만 중단합니다. 실패한 뒤 다음 절로 진행하지 않습니다.

## 4. Linux용 kind 설치

기준 버전은 기존 학습 가이드와 같은 v0.33.0입니다. 기존 Linux kind가 있다면
먼저 `type -a kind`, `kind version`으로 확인하고 중복 설치하지 않습니다.
[공식 Quick Start](https://kind.sigs.k8s.io/docs/user/quick-start/),
[릴리스와 체크섬](https://github.com/kubernetes-sigs/kind/releases/tag/v0.33.0)

```bash
(
  set -euo pipefail
  case "$(uname -m)" in
    x86_64) arch=amd64 ;;
    aarch64|arm64) arch=arm64 ;;
    *) printf '%s\n' 'Unsupported architecture' >&2; exit 1 ;;
  esac
  tmp_dir=$(mktemp -d)
  cd "$tmp_dir"
  kind_version=v0.33.0
  asset="kind-linux-$arch"
  release="https://github.com/kubernetes-sigs/kind/releases/download/$kind_version"
  curl -fSLo "$asset" "$release/$asset"
  curl -fSLo "$asset.sha256sum" "$release/$asset.sha256sum"
  sha256sum --check "$asset.sha256sum"
  test ! -e "$HOME/.local/bin/kind" || { printf '%s\n' 'Existing kind: inspect before replacing.'; exit 1; }
  install -m 0755 "$asset" "$HOME/.local/bin/kind"
)
kind version
```

임시 다운로드는 `mktemp` 전용 디렉터리에 남깁니다.
kind 프로그램 버전과 Kubernetes 노드 버전은 별개입니다.
[3단계](03-kind-kubernetes.md)의 고정 이미지로 클러스터를 생성하며 지금은 생성하지 않습니다.

## 5. Linux용 kubectl 설치

기존 서버 기준은 Kubernetes 1.35.8이며 클라이언트도 같은 버전으로 맞춥니다.
kubectl은 서버와 minor 버전 차이가 1 이내여야 합니다.
Windows Docker Desktop의 kubectl.exe와 혼용하지 않습니다.
[Linux 설치·체크섬 검증](https://kubernetes.io/docs/tasks/tools/install-kubectl-linux/)

```bash
(
  set -euo pipefail
  case "$(uname -m)" in
    x86_64) arch=amd64 ;;
    aarch64|arm64) arch=arm64 ;;
    *) printf '%s\n' 'Unsupported architecture' >&2; exit 1 ;;
  esac
  tmp_dir=$(mktemp -d)
  cd "$tmp_dir"
  kubectl_version=v1.35.8
  url="https://dl.k8s.io/release/$kubectl_version/bin/linux/$arch/kubectl"
  curl -fSLo kubectl "$url"
  curl -fSLo kubectl.sha256 "$url.sha256"
  printf '%s  kubectl\n' "$(tr -d '\r\n' < kubectl.sha256)" | sha256sum --check -
  test ! -e "$HOME/.local/bin/kubectl" || { printf '%s\n' 'Existing kubectl: inspect before replacing.'; exit 1; }
  install -m 0755 kubectl "$HOME/.local/bin/kubectl"
)
kubectl version --client
```

클러스터 생성 후 kubeconfig는 WSL의 `~/.kube/config`에 기록됩니다.
Windows의 `%USERPROFILE%\.kube\config`와 별개이며 파일을 무작정 복사/덮어쓰지 않습니다.
이미 Windows에서 같은 Docker 엔진에 kind 클러스터를 만들었다면 재생성하지 말고
[문제 해결 문서](07-troubleshooting-and-cleanup.md)의 kubeconfig 전환 절차를 따릅니다.

## 6. Linux용 Helm 설치

Helm CLI와 Envoy Gateway chart 버전은 별개입니다.
아래 예시는 Helm v4.3.0 바이너리이며 chart는 기존 가이드의 v1.9.1을 유지합니다.
설치 전 [공식 Helm 설치](https://helm.sh/docs/intro/install/)와
[릴리스](https://github.com/helm/helm/releases)를 확인합니다.

```bash
(
  set -euo pipefail
  case "$(uname -m)" in
    x86_64) arch=amd64 ;;
    aarch64|arm64) arch=arm64 ;;
    *) printf '%s\n' 'Unsupported architecture' >&2; exit 1 ;;
  esac
  tmp_dir=$(mktemp -d)
  cd "$tmp_dir"
  helm_version=v4.3.0
  archive="helm-$helm_version-linux-$arch.tar.gz"
  curl -fSLO "https://get.helm.sh/$archive"
  curl -fSLO "https://get.helm.sh/$archive.sha256sum"
  sha256sum --check "$archive.sha256sum"
  tar -xzf "$archive"
  test ! -e "$HOME/.local/bin/helm" || { printf '%s\n' 'Existing helm: inspect before replacing.'; exit 1; }
  install -m 0755 "linux-$arch/helm" "$HOME/.local/bin/helm"
)
helm version
```

체크섬은 다운로드 무결성 확인입니다. 서명 검증은 공식 문서의 별도 절차를 참고합니다.
모든 다운로드 블록은 공식 HTTPS 주소와 체크섬을 사용하며 실패 시 설치하지 않습니다.
설치만으로 chart가 배포되거나 AWS 리소스가 생성되지는 않습니다.

## 7. Windows VS Code로 WSL 프로젝트 열기

Windows VS Code에 Microsoft의 **WSL** 확장(`ms-vscode-remote.remote-wsl`)을 설치합니다.
프로젝트 위치는 당장 이동하지 않고 기존 Windows 파일을 사용합니다.

```bash
cd /mnt/c/dev/personal/infra/spring-gateway-docker-sample
code .
```

VS Code 화면은 Windows에 뜨지만 왼쪽 아래가 `WSL: <배포판>`이어야 합니다.
일반 Windows 창으로 열렸다면 명령 팔레트의 **WSL: Reopen Folder in WSL**을 실행합니다.
Java/Gradle 확장도 WSL 쪽 설치가 필요한지 확장 화면에서 확인합니다.
`code`가 없다면 Windows VS Code/WSL 확장 설치 후 터미널을 다시 열거나
Windows VS Code의 WSL 연결 명령으로 같은 경로를 엽니다.
[VS Code WSL 공식 안내](https://code.visualstudio.com/docs/remote/wsl)

`/mnt/c/...`는 기존 `C:\...` 파일에 접근하는 것이므로 두 복사본이 생기지 않습니다.
나중에 성능이 필요하면 Linux 파일시스템의 `~/work/...`로 작업 기준을 옮길 수 있지만,
현재 문서 수정에서는 복사/이동하지 않습니다. 이전 시에는 원본 보존, 비밀값/빌드 캐시 구분,
Compose 프로젝트/volume 식별자를 먼저 확인합니다.
[Docker 파일시스템 권장 사항](https://docs.docker.com/desktop/features/wsl/best-practices/)

## 8. 선택: WSL JDK 25와 Gradle Wrapper

Docker 이미지 빌드만 하면 WSL JDK는 필요하지 않습니다. VS Code Java 개발 또는
WSL에서 Wrapper를 실행하려면 **Linux용 JDK 25**가 필요합니다. Windows JDK를 지정하지 않습니다.

Ubuntu/Debian에서는 [Adoptium 공식 Linux 설치](https://adoptium.net/installation/linux)의
저장소를 사용합니다. 아래는 **아직 Adoptium 저장소를 등록하지 않은 경우**의 예시이며,
signing key를 이 저장소에만 적용하도록 signed-by를 지정합니다.
먼저 apt sources에서 기존 등록 여부를 확인하고, 이미 있으면 등록 블록은 생략합니다.

```bash
grep -R -l 'packages.adoptium.net' /etc/apt/sources.list /etc/apt/sources.list.d 2>/dev/null
```

위 조회에 등록 파일이 없다면, 공식 지원 배포판/codename을 확인한 후 등록합니다.

```bash
(
  set -euo pipefail
  sudo apt-get install -y gpg
  test ! -e /etc/apt/keyrings/adoptium.gpg
  test ! -e /etc/apt/sources.list.d/adoptium.list
  tmp_dir=$(mktemp -d)
  curl -fSLo "$tmp_dir/adoptium.asc" https://packages.adoptium.net/artifactory/api/gpg/key/public
  gpg --dearmor --output "$tmp_dir/adoptium.gpg" "$tmp_dir/adoptium.asc"
  distro_codename=$(. /etc/os-release; printf '%s' "$VERSION_CODENAME")
  [[ "$distro_codename" =~ ^[a-z]+$ ]]
  sudo install -d -m 0755 /etc/apt/keyrings
  sudo install -m 0644 "$tmp_dir/adoptium.gpg" /etc/apt/keyrings/adoptium.gpg
  printf 'deb [signed-by=/etc/apt/keyrings/adoptium.gpg] https://packages.adoptium.net/artifactory/deb %s main\n' "$distro_codename" |
    sudo tee /etc/apt/sources.list.d/adoptium.list >/dev/null
)
```

등록 성공 후 설치합니다. apt 오류가 나면 지원되지 않는 codename/네트워크/서명 오류를
먼저 확인하며 검증을 끄거나 다른 Ubuntu 버전의 저장소를 임의로 지정하지 않습니다.


```bash
sudo apt-get update
sudo apt-get install -y temurin-25-jdk
java -version
javac -version
readlink -f "$(command -v javac)"
```

`java`, `javac` 모두 25인지 확인합니다. 다른 버전이 선택되어 있으면 기존 JDK를 삭제하지 말고
`update-alternatives` 또는 별도 JDK 경로로 선택합니다. 선택 완료 후:

```bash
export JAVA_HOME="$(dirname "$(dirname "$(readlink -f "$(command -v javac)")")")"
export PATH="$JAVA_HOME/bin:$HOME/.local/bin:$PATH"
bash ./gradlew --version
```

지속 설정은 실제 JDK 25 경로를 확인해 `~/.bashrc`에 기록합니다.
저장소의 Windows 전용 JDK 경로 고정은 제거했으므로 실행 환경의 JDK 검색/JAVA_HOME을 사용합니다.
VS Code를 WSL로 다시 연결하고 **Java: Configure Java Runtime**에서 실제 JDK를 확인합니다.

Wrapper는 저장소에 포함되어 있어 전역 Gradle 설치가 필요하지 않습니다(현재 9.7.1).
`--version`은 앱 컴파일/테스트를 실행하지 않지만 최초 Gradle 다운로드가 발생할 수 있습니다.
[Gradle Wrapper](https://docs.gradle.org/current/userguide/gradle_wrapper.html)

## 9. 최종 확인과 주의

```bash
type -a docker kubectl kind helm
docker version
docker info --format '{{.OSType}}'
docker compose version
kubectl version --client
kind version
helm version
```

- [ ] 기본 배포판이 개발용 WSL2이며 Docker Desktop의 내부 배포판이 아니다.
- [ ] Docker Server 연결과 Linux 모드를 확인했다.
- [ ] kind/kubectl/Helm이 Linux 실행 파일이며 Windows .exe와 혼용하지 않는다.
- [ ] VS Code 왼쪽 아래에 WSL 연결 상태가 표시된다.
- [ ] Java 개발 시 Linux JDK 25와 Wrapper JVM을 확인했다.
- [ ] 실제 설치한 버전을 기록했다. 이 문서는 전체 조합의 실행 검증 보고가 아니다.

WSL/Windows의 localhost 접근은 NAT/mirrored 설정에 따라 다를 수 있습니다.
Windows에서만 8080이 열리거나 WSL kubectl이 접속하지 못하면 방화벽을 끄거나
`0.0.0.0`으로 노출하지 말고 [WSL 연결 진단](07-troubleshooting-and-cleanup.md)을 먼저 확인합니다.

다음: [01-setup-and-compose.md](01-setup-and-compose.md).
