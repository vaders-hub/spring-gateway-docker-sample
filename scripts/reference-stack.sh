#!/usr/bin/env bash
# 실무 레퍼런스 구성의 단일 진입점. 기존 프로젝트/DB 볼륨을 재사용하고 삭제하지 않는다.
set -euo pipefail
[[ $- != *x* ]] || { printf '%s\n' 'Disable shell tracing first.' >&2; exit 1; }
cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.."
args=(-f docker-compose.yml -f docker-compose.persistence.yml -f docker-compose.keycloak.yml)
# 이미 사용 중인 관측 서비스를 같은 Compose 모델에 유지한다.
if grep -Eq '^GRAFANA_ADMIN_PASSWORD=.+$' .env 2>/dev/null; then
  args+=(-f docker-compose.observability.yml --profile observability)
fi
case "${1:-status}" in
  prepare)
    bash scripts/new-local-env.sh
    bash scripts/ensure-persistence-env.sh
    python3 scripts/ensure-keycloak-env.py
    ;;
  up) docker compose "${args[@]}" up -d --build --wait --wait-timeout 300 ;;
  stop) docker compose "${args[@]}" stop ;;
  status) docker compose "${args[@]}" ps ;;
  config) docker compose "${args[@]}" config --quiet ;;
  *) printf '%s\n' 'Usage: bash scripts/reference-stack.sh prepare|up|stop|status|config' >&2; exit 2 ;;
esac
