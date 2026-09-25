#!/bin/sh
set -eu

LEDGER_HOST=${LEDGER_HOST:-did-canton}
LEDGER_PORT=${LEDGER_PORT:-6865}
JSON_API_URL=${JSON_API_URL:-http://did-canton:7575}
PARTY_FILE=/tmp/did-parties.json

/workspace/scripts/wait-for-synchronizer.sh
curl --fail --silent --show-error "$JSON_API_URL/v2/parties" > "$PARTY_FILE"

party_for_hint() {
  hint=$1
  awk -v prefix="\"party\":\"${hint}::" '
    index($0, prefix) {
      value = substr($0, index($0, prefix) + 9)
      sub(/".*/, "", value)
      print value
      exit
    }
  ' "$PARTY_FILE"
}

dso=$(party_for_hint DSO)
script_name=Canton.Network.Did.Seed:main
args=
if [ -n "$dso" ]; then
  script_name=Canton.Network.Did.Seed:resume
  args="--input-file /tmp/did-seed-input.json"
  printf '{"dso":"%s"}\n' "$dso" > /tmp/did-seed-input.json
fi

# Daml Script accepts the optional party input for reruns; shell expansion is intentional.
# shellcheck disable=SC2086
exec dpm script \
  --dar /artifacts/canton-network-did-demo-scripts-0.1.0.dar \
  --script-name "$script_name" \
  --ledger-host "$LEDGER_HOST" \
  --ledger-port "$LEDGER_PORT" \
  --upload-dar no \
  $args
