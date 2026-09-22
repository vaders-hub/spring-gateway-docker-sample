#!/usr/bin/env bash
set -euo pipefail
set +x
umask 077
project_root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
env_path="$project_root/.env"
[[ -f "$env_path" && ! -L "$env_path" ]] || {
  printf '%s\n' 'Create a regular .env file with bash scripts/new-local-env.sh first.' >&2
  exit 1
}
if grep -q '^GRAFANA_ADMIN_PASSWORD=' "$env_path"; then
  printf '%s\n' 'GRAFANA_ADMIN_PASSWORD exists; no changes made.'
  exit 0
fi
password=$(openssl rand -hex 24)
# Leading newline also handles an existing file without a final newline.
printf '\nGRAFANA_ADMIN_PASSWORD=%s\n' "$password" >> "$env_path"
printf '%s\n' 'Added Grafana password; existing credentials were preserved.'
