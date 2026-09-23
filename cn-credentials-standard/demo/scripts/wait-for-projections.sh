#!/bin/sh
set -eu

CREDENTIAL_INTERFACE='canton-network-credentials-interfaces:Canton.Network.Credentials.V1:Credential'
REGISTERED_CREDENTIAL_INTERFACE='canton-network-credentials-interfaces:Canton.Network.Credentials.V1:RegisteredCredential'
CREDENTIAL_EXPECTED=${CREDENTIAL_SEED_COUNT:-360}
TIMEOUT=${PROJECTION_TIMEOUT_SECONDS:-300}
started=$(date +%s)

while :; do
  result=$(psql -X -v ON_ERROR_STOP=1 -At \
    -c "select (select count(*) from active('$CREDENTIAL_INTERFACE')), (select count(*) from active('$REGISTERED_CREDENTIAL_INTERFACE')), (select count(*) from active('$REGISTERED_CREDENTIAL_INTERFACE') r join active('$CREDENTIAL_INTERFACE') c using (contract_id));" 2>&1) || result="ERROR: $result"

  expected="$CREDENTIAL_EXPECTED|$CREDENTIAL_EXPECTED|$CREDENTIAL_EXPECTED"
  if [ "$result" = "$expected" ]; then
    printf '%s\n' "Credential PQS projections ready: credentials=$CREDENTIAL_EXPECTED joined=$CREDENTIAL_EXPECTED"
    exit 0
  fi

  now=$(date +%s)
  if [ $((now - started)) -ge "$TIMEOUT" ]; then
    printf '%s\n' "Timed out after ${TIMEOUT}s waiting for credential projections; last result: $result" >&2
    exit 1
  fi
  printf '%s\n' "Waiting for credential projections; current result: $result"
  sleep 3
done
