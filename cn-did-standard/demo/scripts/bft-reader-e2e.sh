#!/bin/sh
set -eu

reader="http://localhost:${BFT_READER_PORT:-42007}"
status() { curl --silent --output /dev/null --write-out '%{http_code}' "$1"; }

attempt=0
until curl --fail --silent "$reader/q/health/ready" >/dev/null; do
  attempt=$((attempt + 1))
  if [ "$attempt" -ge 90 ]; then
    docker compose ps >&2 || true
    docker compose logs --no-color --tail=80 bft-reader-api >&2 || true
    exit 1
  fi
  sleep 2
done

[ "$(status "$reader/v1/bft/dids")" = 404 ]
dso_did=$(curl --fail --silent "http://localhost:${DID_API_1_PORT:-42003}/v1/dids/dso" | jq -er '.did')
encoded_did=$(jq -nr --arg value "$dso_did" '$value|@uri')
curl --fail --silent --path-as-is "$reader/v1/bft/dids/$encoded_did" |
  jq -e --arg did "$dso_did" '
    .family == "dids" and .result.did == $did and .quorum.agreement == "4-of-4" and
    .quorum.required == 3 and .quorum.matched == 4 and .quorum.total == 4 and
    (.sources | length) == 4 and
    .result.didDocument.id == $did and .result.didDocument.controller == [$did] and
    (.result.didDocument.service | length) == 6 and
    .result.didDocument.verificationMethod == [] and
    (.result.didDocument.verificationRelationships |
      .authentication == [] and .assertionMethod == [] and .keyAgreement == [] and
      .capabilityInvocation == [] and .capabilityDelegation == [])
  ' >/dev/null

[ "$(status "$reader/v1/bft/unknown")" = 404 ]
[ "$(status "$reader/v1/bft/dids/did%3Acanton%3ADSO%3A%3Aunknown")" = 404 ]

printf '%s\n' "BFT reader E2E passed: singular healthy 4-of-4 DID resolution and quorum-matching DID not-found."
printf '%s\n' "Fault matrix is available through DID_DEMO_FAULTS_ENABLED and the per-instance Compose override; it is not toggled against a live default stack."
