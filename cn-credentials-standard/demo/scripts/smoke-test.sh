#!/bin/sh
set -eu

BASE="http://localhost:${CREDENTIALS_API_PORT:-8080}"
status() { curl --silent --output /dev/null --write-out '%{http_code}' "$1"; }

until curl --fail --silent "$BASE/q/health/ready" >/dev/null; do
  printf '%s\n' "Waiting for PQS-backed API readiness..."
  sleep 2
done

curl --fail --silent "$BASE/q/health/live" | jq -e '.status == "UP"' >/dev/null
curl --fail --silent "$BASE/q/health/ready" | jq -e '.status == "UP"' >/dev/null

intrinsic=$(curl --fail --silent "$BASE/v1/credentials/urn:demo:credential:000")
printf '%s' "$intrinsic" | jq -e '
  .credentialId == "urn:demo:credential:000" and
  (.contractId | type == "string") and
  (.credential.issuer | type == "object") and
  (.credential.credentialTypes | type == "array") and
  (.credential.credentialSubject | type == "array") and
  (.credential.holders | type == "array") and
  (has("registration") | not)
' >/dev/null

registered=$(curl --fail --silent "$BASE/v1/registered-credentials/urn:demo:credential:000")
printf '%s' "$registered" | jq -e '
  .credentialId == "urn:demo:credential:000" and
  (.contractId | type == "string") and
  (.credential | type == "object") and
  (.registration.registryAdmin | type == "string")
' >/dev/null

for family in credentials registered-credentials; do
  first=$(curl --fail --silent "$BASE/v1/$family?page=0&pageSize=50")
  printf '%s' "$first" | jq -e '.page == 0 and .pageSize == 50 and (.items | length == 50) and .hasNext' >/dev/null
  max_page=$(curl --fail --silent "$BASE/v1/$family?page=0&pageSize=100")
  printf '%s' "$max_page" | jq -e '.pageSize == 100 and (.items | length == 100) and .hasNext' >/dev/null
  first_ids=$(printf '%s' "$first" | jq -c '[.items[].credentialId]')
  first_again=$(curl --fail --silent "$BASE/v1/$family?page=0&pageSize=50" | jq -c '[.items[].credentialId]')
  [ "$first_ids" = "$first_again" ]
  [ "$(status "$BASE/v1/$family/missing")" = 404 ]
  [ "$(status "$BASE/v1/$family?page=-1&pageSize=50")" = 400 ]
  [ "$(status "$BASE/v1/$family?page=0&pageSize=101")" = 400 ]
done

[ "$(status "$BASE/api/v1/credentials")" = 404 ]
printf '%s\n' "Smoke passed: canonical intrinsic and registered routes, separate response objects, bounded pages, stable order, errors, and no implicit /api/v1 alias."
