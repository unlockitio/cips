#!/bin/sh
set -eu

LEDGER_HOST=${LEDGER_HOST:-canton}
LEDGER_PORT=${LEDGER_PORT:-6865}
SCRIPTS_DAR=/artifacts/canton-network-did-demo-scripts-0.1.0.dar

[ -f "$SCRIPTS_DAR" ] || {
  printf '%s\n' "DID scripts DAR is missing from the shared Compose artifact volume: $SCRIPTS_DAR" >&2
  exit 1
}

if ! /workspace/scripts/retry.sh "DID seed submission" "${SEED_TIMEOUT_SECONDS:-300}" \
    dpm script \
    --dar "$SCRIPTS_DAR" \
    --script-name Canton.Network.Did.Seed:main \
    --ledger-host "$LEDGER_HOST" \
    --ledger-port "$LEDGER_PORT" \
    --upload-dar no; then
  printf '%s\n' "DID seeding failed. The shared Canton ledger may already contain the deterministic fixture." >&2
  exit 1
fi

printf '%s\n' "Submitted Alice and Bob DID contracts to the shared Canton ledger."
