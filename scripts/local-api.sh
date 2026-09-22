#!/usr/bin/env bash
# Source from the repository root. Do not use shell tracing with credentials.
# .env is parsed as data by jq, never executed with source/eval.
lab_login() {
  if [[ $- == *x* ]]; then
    printf '%s\n' 'Disable tracing with set +x before authentication.' >&2
    return 1
  fi
  unset LAB_TOKEN
  LAB_BASE_URL=${1:-http://localhost:8080}
  case "$LAB_BASE_URL" in
    http://localhost:8080|http://127.0.0.1:8080|http://localhost:8888|http://127.0.0.1:8888|http://localhost:8889|http://127.0.0.1:8889) ;;
    *) printf '%s\n' 'Only the documented local learning endpoints are allowed.' >&2; return 1 ;;
  esac
  local response token
  response=$(
    set -o pipefail
    jq -en --rawfile env .env '
      $env | split("\n") | map(rtrimstr("\r")) |
      map(select(test("^(DEMO_USERNAME|DEMO_PASSWORD)=")) |
        capture("^(?<key>[^=]+)=(?<value>.*)$")) |
      if (map(select(.key == "DEMO_USERNAME")) | length) != 1 or
         (map(select(.key == "DEMO_PASSWORD")) | length) != 1
      then error("Missing or duplicate demo credentials") else from_entries end |
      {username: .DEMO_USERNAME, password: .DEMO_PASSWORD} |
      if (.username | length) == 0 or (.password | length) == 0
      then error("Empty demo credentials") else . end
    ' | curl --silent --show-error --fail --max-time 15 \
      --header 'Content-Type: application/json' --data-binary @- "$LAB_BASE_URL/auth/token"
  ) || { printf '%s\n' 'Login failed; token was not retained.' >&2; return 1; }
  token=$(printf '%s' "$response" | jq -er '.data.accessToken | select(type == "string")') || return 1
  [[ "$token" =~ ^[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+$ ]] || {
    printf '%s\n' 'Unexpected token format.' >&2; return 1
  }
  LAB_TOKEN=$token
  printf '%s\n' 'Login succeeded; token kept only in this shell.'
}

lab_api() {
  # method, path, optional JSON body, optional "status" output mode
  if [[ $- == *x* ]]; then
    printf '%s\n' 'Disable tracing with set +x before API calls.' >&2
    return 1
  fi
  [[ -n ${LAB_TOKEN:-} && -n ${LAB_BASE_URL:-} ]] || {
    printf '%s\n' 'Run lab_login first.' >&2; return 1
  }
  local method=${1:-GET} path=${2:-/api/hello} body=${3:-} mode=${4:-body}
  [[ "$path" == /api/* ]] || { printf '%s\n' 'Expected /api/ path.' >&2; return 1; }
  local -a args=(--silent --show-error --max-time 15 --request "$method"
    --header 'X-Request-Id: local-learning-001')
  if [[ -n "$body" ]]; then
    args+=(--header 'Content-Type: application/json' --data-binary "$body")
  fi
  if [[ "$mode" == status ]]; then
    args+=(--output /dev/null --write-out '%{http_code}\n')
  fi
  # Header travels over stdin rather than the process command line.
  printf 'header = "Authorization: Bearer %s"\n' "$LAB_TOKEN" |
    curl --config - "${args[@]}" "$LAB_BASE_URL$path"
}
