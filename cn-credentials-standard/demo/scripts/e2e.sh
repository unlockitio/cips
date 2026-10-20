#!/bin/sh
set -eu

COMPOSE_PROJECT_NAME=${COMPOSE_PROJECT_NAME:-cn-credential-demo}
export COMPOSE_PROJECT_NAME

# This E2E owns the whole lifecycle of a disposable stack, so it removes its own
# volumes. The Canton participant is in-memory and restarts from Genesis, which
# PQS refuses to prepend onto a retained datastore; keeping volumes would make a
# second run fail. Never point this at data you need to retain.
cleanup() {
  docker compose down --volumes
}
trap cleanup EXIT INT TERM

cleanup
docker compose up --build --detach
./scripts/smoke-test.sh
DID_NAME_RESOLUTION_ENABLED=true COMPOSE_FILE=$PWD/compose.yaml ../../cn-did-standard/demo/scripts/smoke-test.sh
COMPOSE_FILE=$PWD/compose.yaml ../../cn-did-standard/demo/scripts/bft-reader-e2e.sh
./scripts/cross-stack-name-e2e.sh

printf '%s\n' "Unified Credentials and DID E2E passed."
