#!/bin/sh
set -eu

until daml ledger upload-dar /workspace/daml/.daml/dist/canton-network-credentials-demo-0.1.0.dar --host canton --port 6865; do
  printf '%s\n' "Waiting for the Canton synchronizer before uploading the demo DAR..."
  sleep 3
done
