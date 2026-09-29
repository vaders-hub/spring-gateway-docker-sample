#!/usr/bin/env bash
# 저장소 루트/WSL에서 실행한다. 토큰 발급/폐기는 기존 공통 클라이언트 함수를 재사용한다.
set -euo pipefail
if [[ $- == *x* ]]; then
  printf '%s\n' 'Disable shell tracing before authentication.' >&2
  exit 1
fi
cd "$(dirname "${BASH_SOURCE[0]}")/.."
source scripts/local-api.sh

# 자기 테스트에서 만든 토큰만 정리한다. 앱/Redis/DB 설정과 데이터는 변경하지 않는다.
cleanup() {
  if [[ -n ${LAB_TOKEN:-} ]]; then lab_logout >/dev/null || true; fi
}
trap cleanup EXIT

request_status() {
  local path=$1 token=${2:-}
  # 토큰은 명령 인자/출력 대신 curl 설정의 표준입력으로 전달한다.
  { if [[ -n "$token" ]]; then printf 'header = "Authorization: Bearer %s"\n' "$token"; fi; } |
    curl --config - --silent --show-error --max-time 15 --output /dev/null \
      --write-out '%{http_code}' --header 'X-Request-Id: member-security-check' "$LAB_BASE_URL$path"
}
expect_status() {
  local label=$1 expected=$2 path=$3 token=${4:-} actual
  # 공유 rate-limit 버킷의 정상 호출이 429로 섞이지 않게 요청 간격을 둔다.
  sleep 1
  actual=$(request_status "$path" "$token")
  if [[ "$actual" != "$expected" ]]; then
    printf '%s\n' "$label: expected $expected, got $actual" >&2
    return 1
  fi
  printf '%s\n' "$label: $actual"
}

lab_login "${1:-http://localhost:8080}" >/dev/null
expect_status 'Missing JWT' 401 /api/members
expect_status 'Member list' 200 /api/members "$LAB_TOKEN"
expect_status 'Missing member.admin scope' 403 /api/members/admin "$LAB_TOKEN"
expect_status 'Invalid ID format' 400 /api/members/not-a-number "$LAB_TOKEN"
expect_status 'Non-positive ID' 400 /api/members/0 "$LAB_TOKEN"
expect_status 'Missing member' 404 /api/members/9223372036854775807 "$LAB_TOKEN"
# 서명 첫 글자를 바꿔 구조는 유지하면서 서명 검증 실패를 재현한다.
signature=${LAB_TOKEN##*.}
replacement=A
if [[ ${signature:0:1} == A ]]; then replacement=B; fi
tampered="${LAB_TOKEN%.*}.$replacement${signature:1}"
expect_status 'Tampered JWT signature' 401 /api/members "$tampered"
previous=$LAB_TOKEN
lab_logout >/dev/null
expect_status 'Logged-out JWT' 401 /api/members "$previous"
unset previous tampered signature
printf '%s\n' 'Member security checks passed.'
