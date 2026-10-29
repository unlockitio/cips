#!/bin/sh
set -eu

LEDGER_HOST=${LEDGER_HOST:-credential-canton}
LEDGER_PORT=${LEDGER_PORT:-6865}
JSON_API_URL=${JSON_API_URL:-http://credential-canton:7575}
SCRIPTS_DAR=/artifacts/canton-network-credentials-demo-scripts-0.1.0.dar
PARTY_FILE=/tmp/credential-parties.json

[ -f "$SCRIPTS_DAR" ] || {
  printf '%s\n' "Scripts DAR is missing from the Compose artifact volume: $SCRIPTS_DAR" >&2
  exit 1
}

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

issuer=$(party_for_hint DemoIssuer)
holder_a=$(party_for_hint DemoHolderA)
holder_b=$(party_for_hint DemoHolderB)
anchorer=$(party_for_hint DemoAnchorer)
registry_admin=$(party_for_hint DSO)
legacy_admin=$(party_for_hint DemoRegistryAdmin)
if [ -n "$legacy_admin" ]; then
  printf '%s\n' "Legacy DemoRegistryAdmin party exists; refusing to migrate retained volumes to DSO. Use a fresh isolated project with fresh volumes." >&2
  exit 1
fi

script_name=Canton.Network.Credentials.Seed:main
args=
if [ -n "$issuer" ] || [ -n "$holder_a" ] || [ -n "$holder_b" ] || [ -n "$anchorer" ] || [ -n "$registry_admin" ]; then
  [ -n "$issuer" ] && [ -n "$holder_a" ] && [ -n "$holder_b" ] && [ -n "$registry_admin" ] || {
    printf '%s\n' "Credential seed parties are partially allocated; refusing to guess identities." >&2
    exit 1
  }
  [ -n "$anchorer" ] || {
    printf '%s\n' "DemoAnchorer is absent from a retained seed; use a fresh isolated project with fresh volumes." >&2
    exit 1
  }
  script_name=Canton.Network.Credentials.Seed:resume
  args="--input-file /tmp/credential-seed-input.json"
  printf '{"issuer":"%s","holderA":"%s","holderB":"%s","anchorer":"%s","registryAdmin":"%s"}\n' \
    "$issuer" "$holder_a" "$holder_b" "$anchorer" "$registry_admin" > /tmp/credential-seed-input.json
fi

# Daml Script accepts the optional party input for reruns; shell expansion is intentional.
# shellcheck disable=SC2086
dpm script \
  --dar "$SCRIPTS_DAR" \
  --script-name "$script_name" \
  --ledger-host "$LEDGER_HOST" \
  --ledger-port "$LEDGER_PORT" \
  --upload-dar no \
  $args

printf '%s\n' "Verified 360 deterministic demo credentials on the Credentials ledger."
