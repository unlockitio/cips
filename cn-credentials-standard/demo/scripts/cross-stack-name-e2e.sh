#!/bin/sh
set -eu

# Verify both APIs on the already-started unified demo; never reset the ledger.
CREDENTIALS_BASE="http://localhost:${CREDENTIALS_API_PORT:-41003}"
DID_BASE="http://localhost:${DID_API_1_PORT:-42003}"
NETWORK=${CREDENTIALS_NETWORK:-localnet}
LEDGER_BASE="http://localhost:${CANTON_HTTP_PORT:-41001}"
status() { url=$1; shift; curl --silent --output /dev/null --write-out '%{http_code}' --get "$url" "$@"; }

[ "$NETWORK" = localnet ] || { printf '%s\n' 'Cross-stack seed requires localnet.' >&2; exit 1; }
for base in "$CREDENTIALS_BASE" "$DID_BASE"; do
  curl --fail --silent --show-error "$base/q/health/ready" >/dev/null || {
    printf 'Start the unified demo before running this script (%s unavailable).\n' "$base" >&2
    exit 1
  }
done

alice=$(curl --fail --silent --show-error "$CREDENTIALS_BASE/v1/credentials/urn:demo:name:alice.canton" |
  jq -er '.credential.credentialSubject[0].id')
bob=$(curl --fail --silent --show-error "$CREDENTIALS_BASE/v1/credentials/urn:demo:name:bob.canton" |
  jq -er '.credential.credentialSubject[0].id')
[ "$alice" != "$bob" ]
dso=$(curl --fail --silent --show-error "$DID_BASE/v1/dids/dso" | jq -er '.did')
dso_party=${dso#did:canton:}

# Both seeds run on one participant. The DID self-claim is still not naming authority.
parties=$(curl --fail --silent --show-error "$LEDGER_BASE/v2/parties")
printf '%s' "$parties" | jq -e --arg party "$alice" '[.. | objects | select(.party? == $party)] | length == 1' >/dev/null
printf '%s' "$parties" | jq -e --arg party "$dso_party" '[.. | objects | select(.party? == $party)] | length == 1' >/dev/null

attempt=0
until curl --fail --silent --show-error --get \
  --data-urlencode 'alias=urn:fqpn:localnet:demo:alice.canton' \
  "$DID_BASE/v1/demo-name-assertions" |
  jq -e --arg party "$alice" 'length == 1 and .[0].subject == $party' >/dev/null; do
  attempt=$((attempt + 1))
  if [ "$attempt" -ge 60 ]; then
    printf '%s\n' 'Timed out waiting for DID alias projection.' >&2
    exit 1
  fi
  sleep 2
done

# Use the DID document's advertised service URLs as the API consumer would.
services=$(curl --fail --silent --show-error "$DID_BASE/v1/dids/dso" | jq -c '.didDocument.service')
resolve_url=$(printf '%s' "$services" | jq -er --arg id "$dso#name-resolution" '
  .[] | select(.id == $id and .type == "CantonDemoNameResolutionService") |
  select(.serviceEndpoint.version == "1.0" and (.serviceEndpoint.endpoints | length) == 4) |
  .serviceEndpoint.endpoints[0].uri')
reverse_url=$(printf '%s' "$services" | jq -er --arg id "$dso#reverse-name-resolution" '
  .[] | select(.id == $id and .type == "CantonDemoReverseNameResolutionService") |
  select(.serviceEndpoint.version == "1.0" and (.serviceEndpoint.endpoints | length) == 4) |
  .serviceEndpoint.endpoints[0].uri')
# The DID document advertises the four default consumer URLs, not the
# transport overrides of an isolated validation project.
[ "$resolve_url" = "http://localhost:42003/v1/resolve" ]
[ "$reverse_url" = "http://localhost:42003/v1/reverse-resolve" ]
for route in 'name-resolution|CantonDemoNameResolutionService|resolve' 'reverse-name-resolution|CantonDemoReverseNameResolutionService|reverse-resolve'; do
  descriptor=${route%%|*}
  rest=${route#*|}
  kind=${rest%%|*}
  path=${rest#*|}
  printf '%s' "$services" | jq -e --arg id "$dso#$descriptor" --arg kind "$kind" --arg path "$path" '
    [.[] | select(.id == $id and .type == $kind)] | length == 1' >/dev/null
  printf '%s' "$services" | jq -e --arg id "$dso#$descriptor" --arg kind "$kind" --arg path "$path" '
    [.[] | select(.id == $id and .type == $kind)][0].serviceEndpoint.endpoints |
    . as $endpoints | length == 4 and all(range(0;4); . as $i |
      $endpoints[$i].priority == $i and $endpoints[$i].uri == ("http://localhost:" + (42003 + $i | tostring) + "/v1/" + $path))' >/dev/null
done

curl --fail --silent --show-error --get "$CREDENTIALS_BASE/v1/resolve" \
  --data-urlencode 'name=alice.canton' --data-urlencode "network=$NETWORK" \
  --data-urlencode 'source=did' |
  jq -e --arg party "$alice" --arg fqpn "$NETWORK:demo:alice.canton" \
    '.fqpn == $fqpn and .party_id == $party' >/dev/null
curl --fail --silent --show-error --get "$CREDENTIALS_BASE/v1/reverse-resolve" \
  --data-urlencode "party=$alice" --data-urlencode "network=$NETWORK" \
  --data-urlencode 'source=did' |
  jq -e --arg party "$alice" --arg fqpn "$NETWORK:demo:alice.canton" \
    'length == 1 and .[0].fqpn == $fqpn and .[0].party_id == $party' >/dev/null

[ "$(status "$CREDENTIALS_BASE/v1/resolve" --data-urlencode 'name=bob.canton' --data-urlencode "network=$NETWORK" --data-urlencode 'source=did')" = 409 ]
[ "$(status "$CREDENTIALS_BASE/v1/resolve" --data-urlencode 'name=dso.canton' --data-urlencode "network=$NETWORK" --data-urlencode 'source=did')" = 404 ]
[ "$(status "$CREDENTIALS_BASE/v1/resolve" --data-urlencode 'name=unclaimed.canton' --data-urlencode "network=$NETWORK" --data-urlencode 'source=did')" = 404 ]
[ "$(status "$CREDENTIALS_BASE/v1/reverse-resolve" --data-urlencode "party=$bob" --data-urlencode "network=$NETWORK" --data-urlencode 'source=did')" = 200 ]
curl --fail --silent --show-error --get "$CREDENTIALS_BASE/v1/reverse-resolve" \
  --data-urlencode "party=$bob" --data-urlencode "network=$NETWORK" \
  --data-urlencode 'source=did' | jq -e 'length == 0' >/dev/null

# All advertised front doors reach the same Credentials authority; 41003 remains callable.
for port in ${DID_API_1_PORT:-42003} ${DID_API_2_PORT:-42004} ${DID_API_3_PORT:-42005} ${DID_API_4_PORT:-42006}; do
  base="http://localhost:$port"
  curl --fail --silent --show-error --get "$base/v1/resolve" \
    --data-urlencode 'name=alice.canton' --data-urlencode "network=$NETWORK" --data-urlencode 'source=did' |
    jq -e --arg party "$alice" '.party_id == $party' >/dev/null
  curl --fail --silent --show-error --get "$base/v1/reverse-resolve" \
    --data-urlencode "party=$alice" --data-urlencode "network=$NETWORK" --data-urlencode 'source=did' |
    jq -e --arg party "$alice" 'length == 1 and .[0].party_id == $party' >/dev/null
  [ "$(status "$base/v1/resolve" --data-urlencode 'name=bob.canton' --data-urlencode "network=$NETWORK" --data-urlencode 'source=did')" = 409 ]
done
printf '%s\n' 'Cross-stack DID discovery and name resolution passed: four DID proxies, canonical Credentials endpoint, match, reverse, conflict, and not-found.'
