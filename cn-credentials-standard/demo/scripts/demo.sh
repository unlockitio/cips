#!/usr/bin/env bash
set -euo pipefail

compose=(docker compose -p cn-credential-demo -f "$(dirname "$0")/../compose.yaml")
credentials=${CREDENTIALS_API_URL:-http://localhost:41003}
did=${DID_API_URL:-http://localhost:42003}
bft=${CREDENTIAL_BFT_READER_URL:-http://localhost:41007}
party_id=urn:demo:credential:000
external_did=urn:demo:scenario:external-did
holderless=urn:demo:scenario:external-holderless
tampered=urn:demo:scenario:external-tampered
restoring=0

restore() {
  if (( restoring )); then
    "${compose[@]}" up -d --no-deps credential-api-4
    for ((attempt=0; attempt<60; attempt++)); do
      if curl --fail --silent --max-time 3 http://localhost:41006/q/health/ready >/dev/null; then
        restoring=0
        return
      fi
      sleep 2
    done
    printf '%s\n' 'credential-api-4 failed to recover readiness' >&2
    return 1
  fi
}
on_exit() {
  local status=$?
  trap - EXIT
  if (( status != 0 && restoring )); then
    "${compose[@]}" logs --no-color --tail=100 credential-api-4 credential-bft-reader >&2 || true
  fi
  restore || status=1
  exit "$status"
}
trap on_exit EXIT

fetch() { curl --fail --silent --show-error --max-time 15 "$1"; }
check_credential() {
  local id=$1 status=$2 mechanism=$3 score=${4:-}
  local body
  body=$(fetch "$credentials/v1/credentials/$id")
  jq -e --arg id "$id" --arg status "$status" --arg mechanism "$mechanism" --arg score "$score" '
    .credentialId == $id and .verification.status == $status and
    .verification.mechanism == $mechanism and
    .verification.schemaValidation.status == "Success" and
    .canton.holders == [] and (.canton.anchorers[0] | startswith("DemoAnchorer::")) and
    (if $mechanism == "application/vc+jwt" then
      .credential.type == "EnvelopedVerifiableCredential" and
      (.credential.id | startswith("data:application/vc+jwt,"))
     else .credential.proof.cryptosuite == "eddsa-jcs-2022" and
      .credential.credentialSubject[0].achievement.score == ($score | tonumber) and
      (.credential.credentialSubject[0] | has("id") | not) end) and
    (if $status == "FAILED" then
      (.verification.errors | index("Invalid Data Integrity signature") != null) and
      (.verification.errors | all(startswith("Projection mismatch:") | not))
     else .verification.errors == [] end)
  ' <<<"$body" >/dev/null
  if [[ ${show_vectors:-false} == true ]]; then
    jq '{credentialId, credential: {id: .credential.id, type: .credential.type, proof: .credential.proof, credentialSubject: .credential.credentialSubject}, canton: {anchorers: .canton.anchorers, holders: .canton.holders}, verification}' <<<"$body"
  fi
}
pause() { read -r -p "Press Enter for $1..." _; }

mode=${1:-}
[[ -z $mode || $mode == --check ]] || { printf '%s\n' 'Usage: demo.sh [--check]' >&2; exit 2; }
for url in "$credentials/q/health/ready" "$did/q/health/ready" "$bft/q/health/ready"; do
  fetch "$url" | jq -e '.status == "UP"' >/dev/null
done
party=$(fetch "$credentials/v1/credentials/$party_id")
jq -e --arg id "$party_id" '.credentialId == $id and (.credential.issuer | startswith("DemoIssuer::")) and (.credential.issuer as $issuer | .canton.anchorers | index($issuer) != null)' <<<"$party" >/dev/null
check_credential "$external_did" VERIFIED application/vc+jwt
check_credential "$holderless" VERIFIED eddsa-jcs-2022 85
check_credential "$tampered" FAILED eddsa-jcs-2022 86
registered=$(fetch "$credentials/v1/registered-credentials/$tampered")
jq -e '.verification.status == "FAILED" and .verification.schemaValidation.status == "Success" and (.registration.registryAdmin | type == "string")' <<<"$registered" >/dev/null
dso=$(fetch "$did/v1/dids/dso")
dso_id=$(jq -er '.did' <<<"$dso")
jq -e --arg id "$dso_id" '.did == $id and .didDocument.id == $id and .didDocument.alsoKnownAs == ["urn:fqpn:localnet:demo:dso.canton"] and any(.didDocument.service[]; .id == ($id + "#name-resolution") and .type == "CantonDemoNameResolutionService")' <<<"$dso" >/dev/null
bft_url="$bft/v1/bft/credentials/$party_id"
healthy=$(fetch "$bft_url")
jq -e '.quorum.agreement == "4-of-4" and .result.credentialId == "urn:demo:credential:000"' <<<"$healthy" >/dev/null
if [[ $mode == --check ]]; then
  printf '%s\n' 'Demo offline check passed: ready APIs, seeded statuses, DSO discovery and healthy BFT. No service stopped.'
  exit 0
fi
pause 'Party authorization'
jq '{credentialId, issuer: .credential.issuer, anchorers: .canton.anchorers}' <<<"$party"
pause 'two verified external vectors'
show_vectors=true
check_credential "$external_did" VERIFIED application/vc+jwt
check_credential "$holderless" VERIFIED eddsa-jcs-2022 85
pause 'tampered proof and separate schema result'
check_credential "$tampered" FAILED eddsa-jcs-2022 86
pause 'one stopped Credentials host, three agreeing origins'
restoring=1
"${compose[@]}" stop credential-api-4
fault=$(fetch "$bft_url")
jq -e '.quorum.agreement == "3-of-4" and .result.credentialId == "urn:demo:credential:000" and (.sources | length == 4)' <<<"$fault" >/dev/null
jq '{quorum, sources: [.sources[] | {priority, outcome}], credentialId: .result.credentialId}' <<<"$fault"
restore
pause 'DSO DID discovery'
jq '{did, alsoKnownAs: .didDocument.alsoKnownAs, services: [.didDocument.service[] | select(.type == "CantonDemoNameResolutionService") | {id, type, serviceEndpoint}]}' <<<"$dso"
printf '%s\n' 'Demo finished; credential-api-4 restored and ready.'
