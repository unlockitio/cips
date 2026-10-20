#!/bin/sh
set -eu

PORTS="${DID_API_1_PORT:-42003} ${DID_API_2_PORT:-42004} ${DID_API_3_PORT:-42005} ${DID_API_4_PORT:-42006}"
COMPOSE_PROJECT_NAME=${COMPOSE_PROJECT_NAME:-cn-did-demo}
export COMPOSE_PROJECT_NAME
COMPOSE="docker compose"
status() { curl --silent --output /dev/null --write-out '%{http_code}' "$1"; }
diagnostics() {
  $COMPOSE ps >&2 || true
  # The unified stack has no did-pqs service: both products read one shared pipeline,
  # so log whichever PQS service this project actually defines.
  pqs=$($COMPOSE config --services 2>/dev/null | grep -x 'did-pqs\|credential-pqs' | tr '\n' ' ')
  $COMPOSE logs --no-color --tail=80 $pqs did-api-1 did-api-2 did-api-3 did-api-4 >&2 || true
}

for port in $PORTS; do
  base="http://localhost:$port"
  attempt=0
  until curl --fail --silent "$base/q/health/ready" >/dev/null; do
    attempt=$((attempt + 1))
    if [ "$attempt" -ge 90 ]; then
      printf '%s\n' "Timed out waiting for API on port $port." >&2
      diagnostics
      exit 1
    fi
    sleep 2
  done
done

primary="http://localhost:${DID_API_1_PORT:-42003}"
intrinsic=$(curl --fail --silent "$primary/v1/dids?state=all&page=0&pageSize=50")
printf '%s' "$intrinsic" | jq -e '
  .page == 0 and .pageSize == 50 and (.snapshotOffset | type == "number") and
  has("nextPageToken") and (has("observedAt") | not) and
  (.snapshotOffset as $snapshot | all(.items[]; .lifecycle.createdAtOffset <= $snapshot))
' >/dev/null
dso_did=$(curl --fail --silent "$primary/v1/dids/dso" | jq -er '.did')
curl --fail --silent "$primary/v1/dids/capabilities" |
  jq -e '.listings.consistency == "single-pqs-snapshot" and .bft.singularOnly and (.bft.collections | not)' >/dev/null
for state in active archived all; do
  curl --fail --silent "$primary/v1/dids?state=$state" |
    jq -e --arg state "$state" '.snapshotOffset as $snapshot |
      all(.items[]; .lifecycle.createdAtOffset <= $snapshot and
        ($state == "all" or .lifecycle.state == $state))' >/dev/null
done
party=${dso_did#did:canton:}
[ "$dso_did" = "did:canton:$party" ]
encoded_did=$(jq -nr --arg value "$dso_did" '$value|@uri')

expected_services='[
  {"fragment":"did-registries","type":"CantonDidRegistryService","path":"did-registries"},
  {"fragment":"dids","type":"CantonDidResolutionService","path":"dids"},
  {"fragment":"credential-registries","type":"CantonCredentialRegistryService","path":"credential-registries"},
  {"fragment":"credentials","type":"CantonCredentialService","path":"credentials"},
  {"fragment":"asset-registries","type":"CantonAssetRegistryService","path":"asset-registries"},
  {"fragment":"assets","type":"CantonAssetService","path":"assets"},
  {"fragment":"name-resolution","type":"CantonDemoNameResolutionService","path":"resolve"},
  {"fragment":"reverse-name-resolution","type":"CantonDemoReverseNameResolutionService","path":"reverse-resolve"}
]'

if [ "${DID_NAME_RESOLUTION_ENABLED:-false}" != true ]; then
  expected_services=$(printf '%s' "$expected_services" | jq -c '.[0:6]')
fi
instance_number=0
for port in $PORTS; do
  instance_number=$((instance_number + 1))
  base="http://localhost:$port"
  curl --fail --silent "$base/q/health/live" | jq -e '.status == "UP"' >/dev/null
  resolved=$(curl --fail --silent --path-as-is "$base/v1/dids/$encoded_did")
  printf '%s' "$resolved" | jq -e --arg did "$dso_did" --argjson expected "$expected_services" '
    .did == $did and .didDocument.id == $did and
    .didDocument.alsoKnownAs == ["urn:fqpn:localnet:demo:dso.canton"] and
    .didDocument.controller == [$did] and
    .didDocument.verificationMethod == [] and
    (.didDocument.verificationRelationships |
      .authentication == [] and .assertionMethod == [] and .keyAgreement == [] and
      .capabilityInvocation == [] and .capabilityDelegation == []) and
    (.didDocument.service | length) == ($expected | length) and
    ([.didDocument.service[] | {
      fragment: (.id | sub("^" + $did + "#"; "")),
      type,
      path: (.serviceEndpoint.endpoints[0].uri | split("/v1/")[1])
    }] == $expected) and
    all(.didDocument.service[0:6][];
      .serviceEndpoint.version == "1.0" and
      (.serviceEndpoint.endpoints | length) == 4 and
      ([.serviceEndpoint.endpoints[].priority] == [0,1,2,3]) and
      ([.serviceEndpoint.endpoints[].uri | capture("localhost:(?<port>[0-9]+)/").port] == ["42003","42004","42005","42006"])) and
    all(.didDocument.service[6:8][];
      .serviceEndpoint.version == "1.0" and
       (.serviceEndpoint.endpoints | length) == 4 and
       ([.serviceEndpoint.endpoints[].priority] == [0,1,2,3]) and
       ([.serviceEndpoint.endpoints[].uri | capture("localhost:(?<port>[0-9]+)/").port] == ["42003","42004","42005","42006"]))
  ' >/dev/null

  curl --fail --silent "$base/v1/dids/dso" | jq -e --argjson intrinsic "$resolved" '.didDocument == $intrinsic.didDocument' >/dev/null
  curl --fail --silent "$base/v1/dids?state=active" | jq -e --arg did "$dso_did" '
    any(.items[]; .did == $did and .didDocument.alsoKnownAs == ["urn:fqpn:localnet:demo:dso.canton"])' >/dev/null
  registered=$(curl --fail --silent --path-as-is "$base/v1/registered-dids/$encoded_did")
  printf '%s' "$registered" | jq -e --arg did "$dso_did" --arg party "$party" --argjson intrinsic "$resolved" '
    .did == $did and .registration.registryAdmin == $party and
    .didDocument == $intrinsic.didDocument
  ' >/dev/null
  curl --fail --silent --get --data-urlencode "partyId=$party" "$base/v1/registered-dids" |
    jq -e --arg did "$dso_did" '(.items | length) == 1 and .items[0].did == $did and
      .items[0].didDocument.alsoKnownAs == ["urn:fqpn:localnet:demo:dso.canton"]' >/dev/null

  curl --fail --silent --get --data-urlencode "alias=urn:fqpn:localnet:demo:dso.canton" \
    "$base/v1/demo-name-assertions" |
    jq -e --arg did "$dso_did" --arg party "$party" '
      length == 1 and .[0].did == $did and .[0].subject == $party and
      .[0].alias == "urn:fqpn:localnet:demo:dso.canton"
    ' >/dev/null
  # The unified default also seeds the deliberately disagreeing bob.canton self-claim,
  # so assert the DSO self-claim is present and every row is about this exact Party.
  curl --fail --silent --get --data-urlencode "subject=$party" "$base/v1/demo-name-assertions" |
    jq -e --arg party "$party" 'length >= 1 and length <= 10 and
      any(.[]; .alias == "urn:fqpn:localnet:demo:dso.canton") and
      all(.[]; .subject == $party)' >/dev/null
  curl --fail --silent --get --data-urlencode "alias=urn:fqpn:localnet:demo:unclaimed.canton" \
    "$base/v1/demo-name-assertions" | jq -e 'length == 0' >/dev/null
  [ "$(status "$base/v1/demo-name-assertions")" = 400 ]
  [ "$(status "$base/v1/demo-name-assertions?alias=urn:fqpn:localnet:demo:dso.canton&subject=$party")" = 400 ]

  for family in did-registries credential-registries credentials asset-registries assets; do
    curl --fail --silent "$base/v1/$family" | jq -e --arg family "$family" --arg instance "did-api-$instance_number" '
      .family == $family and .apiVersion == "v1" and .instanceId == $instance and .status == "discovery-only"
    ' >/dev/null
  done
  [ "$(status "$base/v1/dids/did%3Acanton%3ADSO%3A%3Aunknown")" = 404 ]
done

for endpoint_port in $PORTS; do
  for path in did-registries dids credential-registries credentials asset-registries assets; do
    curl --fail --silent "http://localhost:$endpoint_port/v1/$path" >/dev/null
  done
done

printf '%s\n' "Smoke passed: runtime DSO DID, four replicas, six core services, $(( $(printf '%s' "$expected_services" | jq 'length') - 6 )) demo name-resolution services, bounded self-claimed alias lookup, and ordered endpoint priorities."
