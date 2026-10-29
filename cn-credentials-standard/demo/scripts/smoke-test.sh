#!/bin/sh
set -eu

BASE="http://localhost:${CREDENTIALS_API_PORT:-41003}"
NETWORK=${CREDENTIALS_NETWORK:-localnet}
# Mirrors the Compose default: the unified stack always configures the DID source.
# Set this to the empty string to assert the absent-capability behaviour instead.
CREDENTIALS_DID_ASSERTIONS_URL=${CREDENTIALS_DID_ASSERTIONS_URL-http://did-api-1:8080/v1/demo-name-assertions}
status() { curl --silent --output /dev/null --write-out '%{http_code}' "$1"; }
verify() {
  curl --fail --silent --show-error --request POST "$BASE/v1/credential-verifications" \
    --header "Content-Type: $1" --data-binary @"$2"
}
fixture_dir=$(dirname "$0")/../fixtures
check_result() {
  printf '%s' "$1" | jq -e --arg status "$2" --arg mechanism "$3" '
    .status == $status and .mechanism == $mechanism and
    (.verifiedAt | type == "string") and
    .schemaValidation.status == "Success" and
    (if $status == "FAILED" then (.errors | index("Invalid Data Integrity signature") != null)
     else .errors == [] end)
  ' >/dev/null
}

until curl --fail --silent "$BASE/q/health/ready" >/dev/null; do
  printf '%s\n' "Waiting for PQS-backed API readiness..."
  sleep 2
done

curl --fail --silent "$BASE/q/health/live" | jq -e '.status == "UP"' >/dev/null
curl --fail --silent "$BASE/q/health/ready" | jq -e '.status == "UP"' >/dev/null

registries=$(curl --fail --silent "$BASE/v1/credential-registries?page=0&pageSize=50")
printf '%s' "$registries" | jq -e '
  .page == 0 and .pageSize == 50 and
  (.hasNext | type == "boolean") and
  (.items | length > 0) and
  ([.items[].registryId] == ([.items[].registryId] | sort)) and
  (all(.items[];
    ((.registryId | type) == "string" and
     (.apiVersion | type) == "string" and
     (.credentials | type) == "object" and
     (.registeredCredentials | type) == "object" and
     (.issuanceFactories | type) == "array")))
' >/dev/null
registry_id=$(printf '%s' "$registries" | jq -r '.items[0].registryId')
registry=$(curl --fail --silent "$BASE/v1/credential-registries/$registry_id")
printf '%s' "$registry" | jq -e --arg registry_id "$registry_id" '
  .registryId == $registry_id and
  .credentials.credentialTypeAggregation.supported == true and
  .credentials.credentialTypeAggregation.credentialCount == false and
  (([.credentials.credentialTypeAggregation.supportedFilters[]] | sort) == ["issuer", "registryAdmin", "state"]) and
  ([.issuanceFactories[] | .contractId] ==
   ([.issuanceFactories[] | .contractId] | sort))
' >/dev/null
[ "$(status "$BASE/v1/credential-registries?page=-1&pageSize=50")" = 400 ]
[ "$(status "$BASE/v1/credential-registries?page=0&pageSize=101")" = 400 ]
[ "$(status "$BASE/v1/credential-registries/missing")" = 404 ]
[ "$(status "$BASE/v1/registry-info")" = 404 ]

intrinsic=$(curl --fail --silent "$BASE/v1/credentials/urn:demo:credential:000")
printf '%s' "$intrinsic" | jq -e '
  .credentialId == "urn:demo:credential:000" and
  (.contractId | type == "string") and
   .credential["@context"][0] == "https://www.w3.org/ns/credentials/v2" and
   (.credential.type | index("VerifiableCredential") != null) and
   (.credential.issuer | type == "string") and
   (.credential.credentialSubject | type == "array") and
   (.canton.holders | type == "array") and
    (.canton.anchorers[0] | type == "string") and
   (has("verification") | not) and (has("schemaValidation") | not) and
   (has("registration") | not)
' >/dev/null

issuer=$(printf '%s' "$intrinsic" | jq -er '.credential.issuer')
types=$(curl --fail --silent "$BASE/v1/credential-types")
printf '%s' "$types" | jq -e '
  .page == 0 and .pageSize == 50 and (.hasNext | type == "boolean") and
  (.items | type == "array") and
  ([.items[] | [.issuer, .credentialType]] == ([.items[] | [.issuer, .credentialType]] | sort)) and
  (all(.items[]; (.issuer | type == "string") and (.credentialType | type == "string") and
    (.firstObservedAt | type == "string") and (.lastObservedAt | type == "string") and
    (has("credentialCount") | not))) and
  (has("credentialCount") | not) and (has("snapshotOffset") | not) and
  (has("nextPageToken") | not)
' >/dev/null
filtered_types=$(curl --fail --silent --get "$BASE/v1/credential-types" --data-urlencode "issuer=$issuer")
printf '%s' "$filtered_types" | jq -e --arg issuer "$issuer" --argjson expected "$(printf '%s' "$intrinsic" | jq -c '.credential.type')" '
  . as $page |
  .page == 0 and .pageSize == 50 and (.hasNext | type == "boolean") and
  (.items | type == "array") and
  (all(.items[]; .issuer == $issuer and (has("credentialCount") | not))) and
  (all($expected[]; . as $type | any($page.items[]; .issuer == $issuer and .credentialType == $type)))
' >/dev/null
first_type_page=$(curl --fail --silent --get "$BASE/v1/credential-types" --data-urlencode "issuer=$issuer" --data-urlencode 'page=0' --data-urlencode 'pageSize=1')
second_type_page=$(curl --fail --silent --get "$BASE/v1/credential-types" --data-urlencode "issuer=$issuer" --data-urlencode 'page=1' --data-urlencode 'pageSize=1')
printf '%s' "$first_type_page" | jq -e --arg issuer "$issuer" '
  .page == 0 and .pageSize == 1 and .hasNext == true and
  (.items | length == 1) and .items[0].issuer == $issuer and
  (.items[0] | has("credentialCount") | not)
' >/dev/null
printf '%s' "$second_type_page" | jq -e --arg issuer "$issuer" --arg first "$(printf '%s' "$first_type_page" | jq -r '.items[0].credentialType')" '
  .page == 1 and .pageSize == 1 and (.hasNext | type == "boolean") and
  (.items | length == 1) and .items[0].issuer == $issuer and
  .items[0].credentialType > $first and (.items[0] | has("credentialCount") | not)
' >/dev/null

for family in credentials registered-credentials; do
  for scenario in external-did external-holderless; do
    anchored=$(curl --fail --silent "$BASE/v1/$family/urn:demo:scenario:$scenario")
    printf '%s' "$anchored" | jq -e --arg scenario "$scenario" --arg family "$family" '
      .credentialId == ("urn:demo:scenario:" + $scenario) and
      (.canton.holders == []) and
      (.canton.anchorers | length == 1) and
      (.canton.anchorers[0] | startswith("DemoAnchorer::")) and
       (has("verification") | not) and (has("schemaValidation") | not) and
      (if $family == "registered-credentials" then (.registration.registryAdmin | type == "string")
       else (has("registration") | not) end) and
      (if $scenario == "external-did" then

        .credential.type == "EnvelopedVerifiableCredential" and
        (.credential.id | startswith("data:application/vc+jwt,"))
      else

        (.credential.issuer | startswith("did:key:")) and
        (.credential.credentialSubject[0] | has("id") | not) and
        .credential.credentialSubject[0].achievement.score == 85 and
        .credential.proof.cryptosuite == "eddsa-jcs-2022"
      end)
    ' >/dev/null
    if [ "$scenario" = external-did ]; then
      jwt=$(printf '%s' "$anchored" | jq -er '.credential.id | select(startswith("data:application/vc+jwt,")) | split(",")[1]')
      result=$(printf '%s' "$jwt" | curl --fail --silent --show-error --request POST "$BASE/v1/credential-verifications" --header 'Content-Type: application/vc+jwt' --data-binary @-)
      check_result "$result" VERIFIED application/vc+jwt
    else
      check_result "$(verify application/vc "$fixture_dir/external-holderless.json")" VERIFIED eddsa-jcs-2022
    fi
  done
  tampered=$(curl --fail --silent "$BASE/v1/$family/urn:demo:scenario:external-tampered")
  printf '%s' "$tampered" | jq -e --arg family "$family" '
    .credentialId == "urn:demo:scenario:external-tampered" and
    .credential.id == .credentialId and
    .credential.credentialSubject[0].achievement.score == 86 and
    .credential.proof.cryptosuite == "eddsa-jcs-2022" and
    .canton.holders == [] and
    (.canton.anchorers | length == 1) and
    (.canton.anchorers[0] | startswith("DemoAnchorer::")) and
    (has("verification") | not) and (has("schemaValidation") | not) and
    (if $family == "registered-credentials" then (.registration.registryAdmin | type == "string")
     else (has("registration") | not) end)
  ' >/dev/null
  check_result "$(verify application/vc "$fixture_dir/external-tampered.json")" FAILED eddsa-jcs-2022
done
check_result "$(verify application/vc+jwt "$fixture_dir/external-did.jwt")" VERIFIED application/vc+jwt
check_result "$(verify application/vc "$fixture_dir/external-holderless.json")" VERIFIED eddsa-jcs-2022
check_result "$(verify application/vc "$fixture_dir/external-tampered.json")" FAILED eddsa-jcs-2022
placeholder=$(curl --fail --silent "$BASE/v1/credentials/urn:demo:scenario:base-credential")
printf '%s' "$placeholder" | jq -e '
  .credentialId == "urn:demo:scenario:base-credential" and
  .credential.type == "EnvelopedVerifiableCredential" and
  (has("verification") | not) and (has("schemaValidation") | not)
' >/dev/null
expired_registration=$(curl --fail --silent "$BASE/v1/registered-credentials/urn:demo:scenario:expired-registration")
printf '%s' "$expired_registration" | jq -e '
  .credentialId == "urn:demo:scenario:expired-registration" and
  (.credential.issuer | startswith("DemoIssuer::")) and
  (.canton.anchorers | length == 1) and
  (.canton.anchorers[0] | startswith("DemoIssuer::")) and
  (.canton.holders | length == 1) and
  (.credential.validUntil | sub("\\.[0-9]+Z$"; "Z") | fromdateiso8601 > now) and
  (.registration.expiresAt | sub("\\.[0-9]+Z$"; "Z") | fromdateiso8601 < now)
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
  printf '%s' "$first" | jq -e '.page == 0 and .pageSize == 50 and (.items | length == 50) and .hasNext and all(.items[]; (has("verification") | not) and (has("schemaValidation") | not))' >/dev/null
  token=$(printf '%s' "$first" | jq -r '.nextPageToken')
  snapshot=$(printf '%s' "$first" | jq -r '.snapshotOffset')
  second=$(curl --fail --silent --get "$BASE/v1/$family" --data-urlencode "nextPageToken=$token" --data-urlencode 'pageSize=50')
  printf '%s' "$second" | jq -e --arg snapshot "$snapshot" '.page == 1 and .snapshotOffset == $snapshot and (.items | length == 50)' >/dev/null
  jump=$(curl --fail --silent --get "$BASE/v1/$family" --data-urlencode "nextPageToken=$token" --data-urlencode 'page=1' --data-urlencode 'pageSize=50')
  [ "$(printf '%s' "$second" | jq -c '[.items[].contractId]')" = "$(printf '%s' "$jump" | jq -c '[.items[].contractId]')" ]
  curl --fail --silent "$BASE/v1/$family?state=all&createdFrom=2000-01-01T00:00:00Z" | jq -e '.snapshotOffset != null and all(.items[]; .lifecycle.createdAtOffset != null)' >/dev/null
  curl --fail --silent "$BASE/v1/$family?state=archived" | jq -e 'all(.items[]; .lifecycle.state == "archived" and (has("verification") | not) and (has("schemaValidation") | not))' >/dev/null
  [ "$(status "$BASE/v1/$family?nextPageToken=invalid")" = 400 ]
  [ "$(status "$BASE/v1/$family?createdFrom=invalid")" = 400 ]
  max_page=$(curl --fail --silent "$BASE/v1/$family?page=0&pageSize=100")
  printf '%s' "$max_page" | jq -e '.pageSize == 100 and (.items | length == 100) and .hasNext' >/dev/null
  first_ids=$(printf '%s' "$first" | jq -c '[.items[].credentialId]')
  first_again=$(curl --fail --silent "$BASE/v1/$family?page=0&pageSize=50" | jq -c '[.items[].credentialId]')
  [ "$first_ids" = "$first_again" ]
  [ "$(status "$BASE/v1/$family/missing")" = 404 ]
  [ "$(status "$BASE/v1/$family?page=-1&pageSize=50")" = 400 ]
  [ "$(status "$BASE/v1/$family?page=0&pageSize=101")" = 400 ]
done

name_credential=$(curl --fail --silent "$BASE/v1/credentials/urn:demo:name:alice.canton")
subject_party=$(printf '%s' "$name_credential" | jq -r '.credential.credentialSubject[0].id')
[ -n "$subject_party" ] && [ "$subject_party" != null ]
resolved=$(curl --fail --silent --get "$BASE/v1/resolve" --data-urlencode 'name=alice.canton' --data-urlencode "network=$NETWORK")
printf '%s' "$resolved" | jq -e --arg party "$subject_party" --arg fqpn "$NETWORK:demo:alice.canton" '.fqpn == $fqpn and .party_id == $party and .display_name == "alice.canton" and (.valid_until | type == "string")' >/dev/null
reverse=$(curl --fail --silent --get "$BASE/v1/reverse-resolve" --data-urlencode "party=$subject_party" --data-urlencode "network=$NETWORK")
printf '%s' "$reverse" | jq -e --arg party "$subject_party" --arg fqpn "$NETWORK:demo:alice.canton" 'length == 1 and .[0].party_id == $party and .[0].fqpn == $fqpn' >/dev/null
[ "$(status "$BASE/v1/resolve?name=alice.canton")" = 400 ]
[ "$(status "$BASE/v1/resolve?name=alice.canton&network=invalid")" = 400 ]
if [ -n "${CREDENTIALS_DID_ASSERTIONS_URL:-}" ]; then
  did_status=$(status "$BASE/v1/resolve?name=alice.canton&network=$NETWORK&source=did")
  case "$did_status" in
    200|404|409|503) : ;;
    *) printf '%s\n' "Unexpected source=did status with a configured DID source: $did_status" >&2; exit 1 ;;
  esac
else
  [ "$(status "$BASE/v1/resolve?name=alice.canton&network=$NETWORK&source=did")" = 422 ]
fi
[ "$(status "$BASE/v1/resolve?name=alice.canton&network=$NETWORK&source=unknown")" = 400 ]
[ "$(status "$BASE/api/v1/credentials")" = 404 ]
printf '%s\n' "Smoke passed: credential records, demo name resolution and reverse resolution, validation errors, and no implicit /api/v1 alias."
