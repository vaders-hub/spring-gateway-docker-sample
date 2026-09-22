#!/usr/bin/env bash
# Local learning credentials only. Never overwrite an existing .env.
set -euo pipefail
set +x
umask 077
project_root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
env_path="$project_root/.env"
if [[ -e "$env_path" || -L "$env_path" ]]; then
  printf '%s\n' '.env already exists; no changes made.'
  exit 0
fi
command -v openssl >/dev/null || { printf '%s\n' 'Install openssl first.' >&2; exit 1; }
jwt_secret=$(openssl rand -hex 32)
demo_password=$(openssl rand -hex 16)
grafana_password=$(openssl rand -hex 24)
# noclobber prevents an intervening file creation from being overwritten.
set -o noclobber
{
  printf 'JWT_ISSUER=local-gateway\nJWT_AUDIENCE=gateway-sample-api\nJWT_SECRET=%s\nJWT_TTL=1h\n' "$jwt_secret"
  printf 'DEMO_USERNAME=demo\nDEMO_PASSWORD=%s\n' "$demo_password"
  printf 'GRAFANA_ADMIN_PASSWORD=%s\n' "$grafana_password"
} > "$env_path"
printf '%s\n' 'Created .env with random local credentials; values were not printed.'
