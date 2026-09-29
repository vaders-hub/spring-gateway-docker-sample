#!/usr/bin/env bash
# 기존 JWT/관측 설정은 보존하고 DB 자격증명이 없을 때만 무작위 값을 추가한다.
set -euo pipefail
set +x
umask 077
project_root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
env_path="$project_root/.env"
[[ -f "$env_path" && ! -L "$env_path" ]] || { printf '%s\n' 'Run scripts/new-local-env.sh first.' >&2; exit 1; }
if grep -q '^POSTGRES_PASSWORD=' "$env_path"; then
  printf '%s\n' 'PostgreSQL credential already configured; unchanged.'
  exit 0
fi
password=$(openssl rand -hex 24)
printf '\nPOSTGRES_PASSWORD=%s\n' "$password" >> "$env_path"
printf '%s\n' 'Added PostgreSQL credential; value not printed.'
