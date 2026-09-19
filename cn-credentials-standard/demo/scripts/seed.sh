#!/bin/sh
set -eu

DAR="daml/.daml/dist/canton-network-credentials-demo-0.1.0.dar"

if [ ! -f "$DAR" ]; then
  printf '%s\n' "Build the demo first with make build." >&2
  exit 1
fi

until daml ledger list-parties --host localhost --port "${LEDGER_PORT:-6865}" >/dev/null 2>&1; do
  printf '%s\n' "Waiting for the Canton Ledger API..."
  sleep 2
done

daml script \
  --dar "$DAR" \
  --script-name Canton.Network.Credentials.Seed:main \
  --ledger-host localhost \
  --ledger-port "${LEDGER_PORT:-6865}" \
  --upload-dar yes

printf '%s\n' "Submitted 360 deterministic demo credentials. Re-run only against a fresh demo volume; logical IDs are intentionally stable."
