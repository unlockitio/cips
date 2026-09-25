#!/bin/sh
set -eu

DID_INTERFACE='canton-network-did-interfaces:Canton.Network.Did.V1:DidDocument'
REGISTERED_DID_INTERFACE='canton-network-did-interfaces:Canton.Network.Did.V1:RegisteredDidDocument'
DID_EXPECTED=${DID_SEED_COUNT:-2}
TIMEOUT=${PROJECTION_TIMEOUT_SECONDS:-300}
started=$(date +%s)

while :; do
  result=$(psql -X -v ON_ERROR_STOP=1 -At \
    -c "select (select count(*) from active('$DID_INTERFACE')), (select count(*) from active('$REGISTERED_DID_INTERFACE')), (select count(*) from active('$REGISTERED_DID_INTERFACE') r join active('$DID_INTERFACE') d using (contract_id));" 2>&1) || result="ERROR: $result"

  expected="$DID_EXPECTED|$DID_EXPECTED|$DID_EXPECTED"
  if [ "$result" = "$expected" ]; then
    if ! psql -X -v ON_ERROR_STOP=1 -At <<SQL
select set_latest(NULL) as snapshot \gset
select validate_offset_exists(:'snapshot'::bigint);
select contract_id, template_fqn, payload_type, create_event_pk, create_event_id, created_at_ix, created_at_offset, created_effective_at, archive_event_pk, archive_event_id, archived_at_ix, archived_at_offset, archived_effective_at from active('$DID_INTERFACE', :'snapshot'::bigint) limit 0;
select create_event_id, created_at_offset from creates('$REGISTERED_DID_INTERFACE', 0::bigint, :'snapshot'::bigint) limit 0;
select archive_event_id, archived_at_offset from archives('$REGISTERED_DID_INTERFACE', 0::bigint, :'snapshot'::bigint) limit 0;
SQL
    then
      printf '%s\n' 'Unsupported PQS installation: DID history requires the public PQS 3.5 bigint signatures and lifecycle columns.' >&2
      exit 1
    fi
    printf '%s\n' "DID PQS projections and historical signatures ready: documents=$DID_EXPECTED joined=$DID_EXPECTED"
    exit 0
  fi

  now=$(date +%s)
  if [ $((now - started)) -ge "$TIMEOUT" ]; then
    printf '%s\n' "Timed out after ${TIMEOUT}s waiting for DID projections; last result: $result" >&2
    exit 1
  fi
  printf '%s\n' "Waiting for DID projections; current result: $result"
  sleep 3
done
