#!/usr/bin/env bash
# Static checks by default. Java and database tests are explicit opt-ins.
set -euo pipefail
project_root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
cd -- "$project_root"
mode=${1:-static}
case "$mode" in static|test|database) ;; *) printf '%s\n' 'Usage: bash scripts/verify.sh [static|test|database]' >&2; exit 2 ;; esac
for script in scripts/*.sh; do bash -n "$script"; done
for directory in k8s k8s/base k8s/overlays/local k8s/overlays/staging k8s/gateway-api; do
  kubectl kustomize "$directory" >/dev/null
done
if [[ -f .env ]]; then
  docker compose config --quiet
  if [[ -n "${GRAFANA_ADMIN_PASSWORD:-}" ]] || grep -Eq '^GRAFANA_ADMIN_PASSWORD=.+$' .env; then
    docker compose -f docker-compose.yml -f docker-compose.observability.yml --profile observability config --quiet
  else
    printf '%s\n' 'Skipped observability Compose validation: run bash scripts/ensure-observability-env.sh to prepare its password.'
  fi
else
  printf '%s\n' 'Skipped Compose validation: .env is absent. Generate local credentials first.'
fi
if grep -Eq '^KC_DB_PASSWORD=.+$' .env 2>/dev/null; then
  bash scripts/reference-stack.sh config
fi
python3 -m json.tool keycloak/gateway-lab-realm.json >/dev/null
printf '%s\n' 'Static checks passed. No deployment or runtime behavior was verified.'
if [[ "$mode" == test ]]; then
  printf '%s\n' 'Explicit test mode: compiling and running tests, including local HTTP test servers.' \
    'Database-tagged tests are excluded. Full proxy/Redis rate-limit behavior requires separate runtime checks.'
  bash ./gradlew :libs:platform-core:test :gateway:test :backend:test
elif [[ "$mode" == database ]]; then
  printf '%s\n' 'Explicit database mode: running database-tagged integration tests with PostgreSQL and Redis Testcontainers. JDK 25 and Docker are required.'
  bash ./gradlew :backend:databaseTest
fi
