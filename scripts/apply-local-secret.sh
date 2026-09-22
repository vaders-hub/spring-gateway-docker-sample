#!/usr/bin/env bash
# Apply only application credentials to the local learning cluster.
set -euo pipefail
set +x
project_root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
cd -- "$project_root"
[[ -f .env ]] || { printf '%s\n' 'Run bash scripts/new-local-env.sh first.' >&2; exit 1; }
command -v jq >/dev/null
command -v kubectl >/dev/null
kubectl --context kind-gateway-lab get namespace gateway-lab >/dev/null
# Generated .env uses unquoted KEY=value lines. Never source it or print values.
# Use data rather than stringData so last-applied does not contain plaintext.
jq -en --rawfile env .env '
  ($env | split("\n") | map(sub("\r$"; ""))
    | map(select(test("^(JWT_SECRET|DEMO_USERNAME|DEMO_PASSWORD)="))
      | capture("^(?<key>[^=]+)=(?<value>.*)$"))) as $entries
  | if ($entries | length) != 3 or ($entries | map(.key) | unique | length) != 3
    then error("Required credential keys must each appear exactly once.")
    else ($entries | from_entries) end
  | if (.JWT_SECRET | length) < 32 or (.DEMO_PASSWORD | length) < 12
       or (.DEMO_USERNAME | length) == 0
    then error("Local credentials are missing or too short.") else . end
  | {apiVersion: "v1", kind: "Secret",
      metadata: {name: "gateway-demo-secret", namespace: "gateway-lab"},
      type: "Opaque", data: with_entries(.value |= @base64)}
' | kubectl --context kind-gateway-lab apply -f -
printf '%s\n' 'Applied JWT_SECRET, DEMO_USERNAME, DEMO_PASSWORD only; values were not printed.'
