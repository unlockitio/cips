#!/bin/sh
set -eu

DEMO_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)

exec dpm sandbox \
  --dar "$DEMO_DIR/model/.daml/dist/canton-network-did-demo-0.1.0.dar" \
  --config "$DEMO_DIR/config/sandbox.conf"
