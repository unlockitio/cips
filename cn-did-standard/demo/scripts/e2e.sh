#!/bin/sh
set -eu

COMPOSE_PROJECT_NAME=${COMPOSE_PROJECT_NAME:-cn-did-demo}
export COMPOSE_PROJECT_NAME

# Validate an already running stack without recreating or removing user resources.
./scripts/smoke-test.sh
./scripts/bft-reader-e2e.sh

printf '%s\n' "DID and BFT reader E2E passed."
