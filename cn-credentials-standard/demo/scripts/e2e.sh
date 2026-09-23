#!/bin/sh
set -eu

COMPOSE_PROJECT_NAME=${COMPOSE_PROJECT_NAME:-cn-credential-demo}
export COMPOSE_PROJECT_NAME

cleanup() {
  docker compose down
}
trap cleanup EXIT INT TERM

docker compose up --build --detach
./scripts/smoke-test.sh

printf '%s\n' "Credential E2E passed."
