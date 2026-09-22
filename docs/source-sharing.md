# Git 소스 공유와 새 환경에서 시작하기

기본 브랜치는 `main`입니다. Git 초기화만으로 커밋/원격 업로드가 발생하지 않습니다.
아래는 검토 후 사용자가 실행할 절차이며 WSL Bash 기준입니다.

## 공유 범위

| 공유 | 제외 |
|---|---|
| 소스·테스트·Gradle 설정과 Wrapper JAR | .gradle, .gradle-user-home, build, target, bin |
| Docker·Compose·Kubernetes·관측 설정 | 실제 .env, .env.*, Secret YAML, 개인 kubeconfig·AWS 자격증명 |
| 비밀값이 비어 있는 .env.example | 개인 키·keystore, 로그·heap dump |
| 학습 문서·스크립트·공통 VS Code 설정 | IDE 개인 설정·임시 파일 |

`.gitignore`는 비밀값 탐지기가 아닙니다. 다른 이름의 파일, 문서/스크린샷, 코드에 직접 넣은 값은
별도로 검토해야 합니다. 이미 커밋한 값은 ignore를 추가해도 기록에서 사라지지 않습니다.
노출됐다면 먼저 자격증명을 폐기/회전하고 이력 정리를 별도로 처리합니다.

`.env.example`은 항목 참고용이며 복사만으로 기동되지 않습니다.
각 개발자는 `bash scripts/new-local-env.sh`로 자신의 `.env`를 생성합니다.

## 최초 커밋 전

```bash
cd /mnt/c/dev/personal/infra/spring-gateway-docker-sample
git status --short --branch
git check-ignore -v .env .env.local .gradle .gradle-user-home k8s/secret.yaml
git status --short --untracked-files=all
```

Windows에서 초기화한 이 폴더를 WSL에서 사용하면 다시 init할 필요가 없습니다.
동일한 .git에 Windows/WSL Git 명령을 동시에 실행하지 않습니다.
소유권 경고는 실제 소유자와 저장소 신뢰 여부를 먼저 확인하고 해결합니다.
확인한 단일 저장소만 safe.directory 예외를 사용할 수 있으며 `safe.directory=*`는 사용하지 않습니다.
Windows와 WSL Git의 사용자 설정은 별개이므로 한쪽 예외가 다른 쪽에도 적용된다고 가정하지 않습니다.

Git 이름/이메일이 없다면 실제 본인 정보로 저장소에만 설정합니다.
아래 placeholder를 바꾸기 전에는 실행하지 마세요. 이메일은 커밋에 기록됩니다.

```bash
git config --local user.name 'YOUR_NAME'
git config --local user.email 'YOUR_COMMIT_EMAIL'
```

공유 대상만 스테이징하고 목록을 확인합니다.

```bash
git add -- .editorconfig .env.example .gitattributes .gitignore .dockerignore \
  .vscode/settings.json .vscode/extensions.json README.md build.gradle settings.gradle \
  gradlew gradlew.bat gradle gateway backend k8s observability scripts docs
# Linux clone에서 ./gradlew로도 실행할 수 있도록 실행 비트 기록
git update-index --chmod=+x gradlew
git diff --cached --name-status
git diff --cached --check
```

`git diff --cached` 내용 검토는 화면 공유/로그 수집을 끈 개인 화면에서 수행합니다.
비밀값이 있으면 커밋하지 말고 스테이징에서 해당 파일을 제외합니다. 검토 후:

```bash
git commit -m "chore: initialize local Kubernetes learning sample"
```

.gitattributes는 기본 LF, PowerShell/batch는 CRLF, JAR는 binary로 관리합니다.
전역 core.autocrlf 변경이나 생성물 강제 추가(`git add -f`)는 필요하지 않습니다.

## 원격 연결 — 별도 선택 후

GitHub/GitLab에 빈 저장소를 만들고 소유자/공개 범위를 결정합니다.
README/LICENSE를 원격에서 자동 생성하지 않으면 최초 이력 충돌을 피할 수 있습니다.
원격 URL에 토큰/비밀번호를 넣지 않습니다. 기존 origin이 있으면 add하지 말고 대상을 확인합니다.

```bash
git remote -v
read -r -p 'Remote clone URL: ' remote_url
git remote add origin "$remote_url"
git push -u origin main
```

기존 원격 이력이 있다면 강제 push하지 않습니다. SSH agent/자격증명 관리자를 사용하세요.
라이선스는 저작권자와 배포 의도를 정해야 하므로 임의로 추가하지 않았습니다.
외부 공개 전에 소유권·재배포 조건·제3자 라이선스를 검토하고 LICENSE를 선택합니다.

## 다른 PC / WSL에서 clone

[Windows + WSL2 준비](learning/00-windows-tool-installation.md)를 먼저 완료합니다.
WSL에 Git이 없다면 Ubuntu/Debian에서는 `sudo apt-get install -y git`을 사용합니다.
기존 폴더를 덮어쓰지 않는 새 경로에서 실행합니다.

```bash
mkdir -p "$HOME/work"
cd "$HOME/work"
read -r -p 'Remote clone URL: ' remote_url
git clone "$remote_url" spring-gateway-docker-sample || exit 1
cd spring-gateway-docker-sample || exit 1
code .
bash scripts/new-local-env.sh
docker compose config --quiet
```

VS Code 화면은 Windows, 실행 환경은 WSL 연결이어야 합니다. Java 확장은 WSL 쪽에 설치하며,
Windows VS Code에는 별도로 Microsoft WSL 확장이 필요합니다.
학습 문서의 /mnt/c/dev/personal/infra/spring-gateway-docker-sample은 자신의 clone 경로로 바꿉니다.
소스 공유에 실행 중인 컨테이너/캐시/kubeconfig는 포함되지 않습니다.
기동은 [Compose](learning/01-setup-and-compose.md), 클러스터는
[kind](learning/03-kind-kubernetes.md) 문서에 따라 별도로 구성합니다.
