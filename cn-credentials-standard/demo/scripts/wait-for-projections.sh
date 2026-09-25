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
    snapshot=$(psql -X -v ON_ERROR_STOP=1 -At -c 'select set_latest(NULL)')
    psql -X -v ON_ERROR_STOP=1 -c "select validate_offset_exists($snapshot::bigint)" >/dev/null
    for interface in "$CREDENTIAL_INTERFACE" "$REGISTERED_CREDENTIAL_INTERFACE"; do
      for reader in "active('$interface', $snapshot::bigint)" "creates('$interface', 0::bigint, $snapshot::bigint)" "archives('$interface', 0::bigint, $snapshot::bigint)"; do
        psql -X -v ON_ERROR_STOP=1 -c "select contract_id,payload,template_fqn,payload_type,create_event_pk,create_event_id,created_at_ix,created_at_offset,created_effective_at,archive_event_pk,archive_event_id,archived_at_ix,archived_at_offset,archived_effective_at from $reader limit 0" >/dev/null
      done
    done
    printf '%s\n' "Credential PQS projections ready: credentials=$CREDENTIAL_EXPECTED joined=$CREDENTIAL_EXPECTED snapshot=$snapshot"
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
