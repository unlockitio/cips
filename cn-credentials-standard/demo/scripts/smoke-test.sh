#!/bin/sh
set -eu

BASE="http://localhost:${API_PORT:-8080}"

status() {
  curl --silent --output /dev/null --write-out '%{http_code}' "$1"
}

until curl --fail --silent "$BASE/health/ready" >/dev/null; do
  printf '%s\n' "Waiting for PQS-backed API readiness..."
  sleep 2
done

curl --fail --silent "$BASE/health/live" | jq -e '.status == "UP"' >/dev/null
curl --fail --silent "$BASE/health/ready" | jq -e '.status == "UP"' >/dev/null

lookup=$(curl --fail --silent "$BASE/api/v1/credentials/urn:demo:credential:000")
printf '%s' "$lookup" | jq -e '
  .credentialId == "urn:demo:credential:000" and
  (.contractId | type == "string") and
  (.issuer | type == "object") and
  (.credentialTypes | type == "array") and
  (.subjects | type == "array") and
  (.holders | type == "array") and
  (.registryAdmin | type == "string")
' >/dev/null

first=$(curl --fail --silent "$BASE/api/v1/credentials?page=0&pageSize=50")
printf '%s' "$first" | jq -e '.page == 0 and .pageSize == 50 and (.items | length == 50) and .hasNext' >/dev/null

default=$(curl --fail --silent "$BASE/api/v1/credentials")
printf '%s' "$default" | jq -e '.page == 0 and .pageSize == 50 and (.items | length == 50) and .hasNext' >/dev/null

middle=$(curl --fail --silent "$BASE/api/v1/credentials?page=3&pageSize=100")
printf '%s' "$middle" | jq -e '.page == 3 and .pageSize == 100 and (.items | length == 60) and (.hasNext | not)' >/dev/null

final=$(curl --fail --silent "$BASE/api/v1/credentials?page=7&pageSize=50")
printf '%s' "$final" | jq -e '.page == 7 and .pageSize == 50 and (.items | length == 10) and (.hasNext | not)' >/dev/null

beyond=$(curl --fail --silent "$BASE/api/v1/credentials?page=8&pageSize=50")
printf '%s' "$beyond" | jq -e '.page == 8 and .pageSize == 50 and (.items | length == 0) and (.hasNext | not)' >/dev/null

max_page=$(curl --fail --silent "$BASE/api/v1/credentials?page=0&pageSize=100")
printf '%s' "$max_page" | jq -e '.pageSize == 100 and (.items | length == 100) and .hasNext' >/dev/null

first_ids=$(printf '%s' "$first" | jq -c '[.items[].credentialId]')
first_again=$(curl --fail --silent "$BASE/api/v1/credentials?page=0&pageSize=50" | jq -c '[.items[].credentialId]')
[ "$first_ids" = "$first_again" ]

[ "$(status "$BASE/api/v1/credentials/missing")" = 404 ]
[ "$(status "$BASE/api/v1/credentials?page=-1&pageSize=50")" = 400 ]
[ "$(status "$BASE/api/v1/credentials?page=bogus&pageSize=50")" = 400 ]
[ "$(status "$BASE/api/v1/credentials?page=0&pageSize=0")" = 400 ]
[ "$(status "$BASE/api/v1/credentials?page=0&pageSize=bogus")" = 400 ]
[ "$(status "$BASE/api/v1/credentials?page=0&pageSize=101")" = 400 ]

printf '%s\n' "Smoke tests passed: health, exact/missing lookup, default/first/middle/final/beyond/max pages, invalid bounds, and deterministic static order."
