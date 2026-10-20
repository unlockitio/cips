#!/usr/bin/env bash
set -euo pipefail

# Generate evidence only after every request and semantic check has succeeded.
python3 - "$(dirname "$0")/../validation/per-cip-api-demonstration.md" <<'PY'
import hashlib
import json
import datetime
import os
import pathlib
import shlex
import subprocess
import sys
from urllib.parse import quote, urlencode

output = pathlib.Path(sys.argv[1]).resolve()
credential_url = os.environ.get("CREDENTIALS_API_URL", "http://localhost:41003").rstrip("/")
did_url = os.environ.get("DID_API_URL", "http://localhost:42003").rstrip("/")
ledger_url = os.environ.get("CANTON_HTTP_URL", "http://localhost:41001").rstrip("/")
credential_hosts = [f"http://localhost:{os.environ.get(f'CREDENTIALS_API_{i}_PORT', 41002 + i)}" for i in range(1, 5)]
credential_bft_url = os.environ.get("CREDENTIAL_BFT_READER_URL", "http://localhost:41007").rstrip("/")


def capture(url, expected="200"):
    command = ["curl", "--silent", "--show-error", "--max-time", "40", "--write-out", "\n__CURL_METADATA__%{http_code} %{content_type}", url]
    process = subprocess.run(command, capture_output=True, text=True, check=False)
    if process.returncode:
        raise RuntimeError(f"curl failed (exit {process.returncode}): {shlex.join(command)}\n{process.stderr}")
    marker = "\n__CURL_METADATA__"
    if marker not in process.stdout:
        raise RuntimeError(f"Missing curl metadata: {shlex.join(command)}")
    body, metadata = process.stdout.rsplit(marker, 1)
    status, content_type = metadata.strip().split(" ", 1)
    if status != expected:
        raise RuntimeError(f"Unexpected HTTP {status} {content_type}: {shlex.join(command)}\n{body}")
    if "json" not in content_type.lower():
        raise RuntimeError(f"Non-JSON response (text body follows, not published as JSON): {shlex.join(command)}\n```text\n{body}\n```")
    try:
        parsed = json.loads(body)
    except json.JSONDecodeError as error:
        raise RuntimeError(f"Unparseable JSON response (text body follows, not published as JSON): {shlex.join(command)}: {error}\n```text\n{body}\n```") from error
    return command, status, content_type, process.returncode, parsed, body


parties_call = capture(ledger_url + "/v2/parties")
parties = parties_call[4]
if not isinstance(parties, dict) or not isinstance(parties.get("partyDetails"), list):
    raise RuntimeError("Ledger party discovery returned an unexpected JSON shape")


def party(hint):
    matches = [item["party"] for item in parties["partyDetails"] if isinstance(item, dict) and isinstance(item.get("party"), str) and item["party"].startswith(hint + "::")]
    if len(matches) != 1:
        raise RuntimeError(f"Expected exactly one {hint} party; found {len(matches)}")
    return matches[0]


alice = party("DemoHolderA")
issuer = party("DemoIssuer")
dso = party("DSO")
if issuer == dso:
    raise RuntimeError("DemoIssuer must differ from the DSO registry admin")

rows = []


def add(cip, objective, limitation, url, check=None):
    result = capture(url)
    if url.startswith(credential_url + "/v1/credentials/") and not vc2(result[4]):
        raise RuntimeError(f"Credential is not unsigned VC2-shaped JSON: {url}")
    if check is not None and not check(result[4]):
        raise RuntimeError(f"Response failed semantic check for {objective}: {url}\n{json.dumps(result[4], indent=2)}")
    rows.append((cip, objective, limitation, result))


def credential(logical_id):
    return credential_url + "/v1/credentials/" + quote(logical_id, safe="")


def exact_id(logical_id):
    return lambda body: isinstance(body, dict) and body.get("credentialId") == logical_id


def view(body):
    return body["credential"]


def subject(body):
    return view(body)["credentialSubject"][0]


def claims(body):
    return {key: value for key, value in subject(body).items() if key != "id"}


def vc2(body):
    credential = body.get("credential") if isinstance(body, dict) else None
    if not isinstance(credential, dict) or credential.get("@context") != ["https://www.w3.org/ns/credentials/v2"] or not isinstance(credential.get("type"), list) or "VerifiableCredential" not in credential["type"] or not isinstance(credential.get("issuer"), str):
        return False
    subjects = credential.get("credentialSubject")
    def native(value):
        return not isinstance(value, dict) or (set(value) != {"tag", "value"} and all(native(item) for item in value.values()))
    return isinstance(subjects, list) and bool(subjects) and all(isinstance(item, dict) and native(item) and ("id" not in item or isinstance(item["id"], str)) for item in subjects)


def textual_issuer(body, value):
    return view(body)["issuer"] == value


def party_subject(body, value):
    return subject(body)["id"] == value


add("#204 base", "Read a base credential with textual external issuer, absent subject ID, and empty claims.", "Does not prove that the external issuer is a Canton Party or that the credential is externally verified.", credential("urn:demo:scenario:base-credential"), lambda body: exact_id("urn:demo:scenario:base-credential")(body) and textual_issuer(body, "https://external.example/issuer") and "id" not in subject(body) and claims(body) == {})
add("#204 external VC", "Read the external DID issuer and student subject with nested achievement and no holders.", "Does not prove verification of an external VC or control of either DID.", credential("urn:demo:scenario:external-did"), lambda body: exact_id("urn:demo:scenario:external-did")(body) and textual_issuer(body, "did:ebsi:example-issuer") and subject(body).get("id") == "did:key:example-student" and "achievement" in claims(body))
add("#204 holderless", "Read the did:key issuer with absent subject and no holders.", "Does not prove holder delegation.", credential("urn:demo:scenario:external-holderless"), lambda body: exact_id("urn:demo:scenario:external-holderless")(body) and textual_issuer(body, "did:key:example-issuer") and "id" not in subject(body))
add("#204 generic profile", "Read a Party subject Alice and a different Party holder Bob.", "Does not prove that subject and holder are interchangeable.", credential("urn:demo:scenario:generic-party-profile"), lambda body: exact_id("urn:demo:scenario:generic-party-profile")(body) and party_subject(body, alice) and body.get("canton", {}).get("holders") == [party("DemoHolderB")])
add("#169 profile", "Read Alice as both Party subject and holder with cip-<nr>/ claim keys.", "Does not prove all profile claims are externally attested.", credential("urn:demo:scenario:cip169-profile"), lambda body: exact_id("urn:demo:scenario:cip169-profile")(body) and party_subject(body, alice) and body.get("canton", {}).get("holders") == [alice] and any(key.startswith("cip-") for key in claims(body)))
add("#209 names", "Read Alice's name credential by exact logical ID.", "Does not prove every name is resolvable on every network.", credential("urn:demo:name:alice.canton"), exact_id("urn:demo:name:alice.canton"))
add("#209 resolution", "Resolve alice.canton on localnet.", "Does not prove DNS ownership or cross-network resolution.", credential_url + "/v1/resolve?" + urlencode({"name": "alice.canton", "network": "localnet"}))
add("#249", "Read the unverified dns textual issuer and six cprp/* claim keys.", "Does not prove DNS verification or issuer control.", credential("urn:demo:scenario:dns-unverified"), lambda body: exact_id("urn:demo:scenario:dns-unverified")(body) and textual_issuer(body, "dns") and len([key for key in claims(body) if key.startswith("cprp/")]) == 6)
add("DID", "Read the DSO DID document advertising the cross-product services.", "Does not prove that an advertised service endpoint is reachable from this host.", did_url + "/v1/dids/dso")
add("DID assertions", "Read the DID-side assertion for Alice's localnet alias.", "Does not prove that conflicting aliases are silently resolved.", did_url + "/v1/demo-name-assertions?" + urlencode({"alias": "urn:fqpn:localnet:demo:alice.canton"}), lambda body: isinstance(body, list) and any(item.get("subject") == alice for item in body))
add("Reverse resolve Alice", "Reverse-resolve Alice's exact Canton Party subject ID on localnet.", "Does not prove issuer or holder IDs are valid substitutes for subject IDs.", credential_url + "/v1/reverse-resolve?" + urlencode({"party": alice, "network": "localnet"}), lambda body: isinstance(body, list) and any(item.get("party_id") == alice for item in body))
add("Reverse resolve DID", "Reverse-resolve that same Alice subject through DID assertions.", "Does not prove that every DID alias is conflict-free.", credential_url + "/v1/reverse-resolve?" + urlencode({"party": alice, "network": "localnet", "source": "did"}), lambda body: isinstance(body, list) and any(item.get("party_id") == alice for item in body))
add("Reverse resolve unbound", "Reverse-resolve the unbound DemoIssuer Party subject ID.", "Does not prove that a 404 is expected for an unbound party; an empty array is the expected 200 response.", credential_url + "/v1/reverse-resolve?" + urlencode({"party": issuer, "network": "localnet"}), lambda body: body == [])

revision = subprocess.run(["git", "rev-parse", "HEAD"], capture_output=True, text=True, check=True, cwd=output.parent).stdout.strip()
dirty = bool(subprocess.run(["git", "status", "--porcelain"], capture_output=True, text=True, check=True, cwd=output.parent).stdout.strip())
lines = ["# Per-CIP API demonstration", "", "Generated by `demo/scripts/per-cip-curls.sh` from live curl responses. No cached PQS or prior-run bodies are used.", "", f"Captured UTC: {datetime.datetime.now(datetime.timezone.utc).isoformat()}; revision: `{revision}`; working tree dirty: `{dirty}`.", f"Credentials: `{credential_url}`; DID: `{did_url}`; ledger: `{ledger_url}`; BFT: `{os.environ.get('BFT_READER_URL', 'http://localhost:42007')}`.", "One Canton participant, one PQS and one PostgreSQL database serve both APIs. Four DID HTTP front doors proxy the one Credentials name authority; they are not independent name authorities or a name-resolution quorum.", "Historical note: the 2026-10-18 capture used the old LF HTTP contract. This run uses the VC2 projection; retained volumes from prior in-memory Canton runs are not reused.", "", "## Credentials / VC", "", "### #209 holding: no HTTP demonstration", "", "Objective: identify the boundary for `urn:demo:holding:team.canton`.", "Does not prove: that the holding is available over HTTP. `PqsCredentialQueryClient` queries the Credential and RegisteredCredential interfaces only, not the Holding interface or DemoNameHolding template. Exposing holdings over HTTP is a separate approval-dependent slice; no successful curl is claimed.", ""]
for cip, objective, limitation, (command, status, content_type, exit_code, body, raw_body) in rows:
    rendered = json.dumps(body, indent=2, ensure_ascii=False)
    if json.loads(rendered) != json.loads(raw_body):
        raise RuntimeError(f"Published JSON differs structurally from raw curl body: {cip}")
    if "\n" not in rendered and rendered not in ("[]", "{}"):
        raise RuntimeError(f"Published JSON is unexpectedly single-line: {cip}")
    if any((len(line) - len(line.lstrip(" "))) % 2 for line in rendered.splitlines()):
        raise RuntimeError(f"Published JSON has non-two-space indentation: {cip}")
    lines.extend([f"### {cip}", "", f"Objective: {objective}", f"Does not prove: {limitation}", "", "```sh", shlex.join(command), "```", f"HTTP status: {status}", f"Content-Type: {content_type}", f"curl exit code: {exit_code}", "", "```json", rendered, "```", ""])
output.parent.mkdir(parents=True, exist_ok=True)
registered = capture(credential_url + "/v1/registered-credentials/" + quote("urn:demo:credential:000", safe=""))
did_document = capture(did_url + "/v1/dids/dso")
did_id = did_document[4]["did"]
if did_document[4].get("did") != "did:canton:" + dso:
    raise RuntimeError("DID registry admin differs from ledger DSO")
registry = capture(credential_url + "/v1/credential-registries")
if not any(item.get("registryId") == dso for item in registry[4].get("items", [])):
    raise RuntimeError("Credentials registry admin differs from ledger DSO")
if not vc2(registered[4]):
    raise RuntimeError("Registered credential is not unsigned VC2-shaped JSON")
shared_id = "urn:demo:name:alice.canton"
credential_host_results = [capture(host + "/v1/credentials/" + quote(shared_id, safe="")) for host in credential_hosts]
if any(not vc2(result[4]) or result[4].get("credentialId") != shared_id or result[4] != credential_host_results[0][4] for result in credential_host_results):
    raise RuntimeError("Four Credentials hosts disagree on the VC2 credential")
credential_bft = capture(credential_bft_url + "/v1/bft/credentials/" + quote(shared_id, safe=""))
if credential_bft[4].get("result") != credential_host_results[0][4] or credential_bft[4].get("quorum", {}).get("agreement") != "4-of-4":
    raise RuntimeError("Credentials BFT reader did not agree with four hosts")
credential_bft_missing = capture(credential_bft_url + "/v1/bft/credentials/" + quote("urn:demo:absent", safe=""), "404")
if credential_bft_missing[4].get("error") != "not_found" or credential_bft_missing[4].get("status") != 404:
    raise RuntimeError("Credentials BFT missing-id semantics changed")
credential_bft_one_fault = None
if os.environ.get("CREDENTIAL_BFT_ONE_FAULT") == "true":
    project = os.environ["COMPOSE_PROJECT_NAME"]
    compose = ["docker", "compose", "-p", project, "-f", str(output.parent.parent / "compose.yaml")]
    subprocess.run(compose + ["stop", "credential-api-4"], check=True)
    try:
        credential_bft_one_fault = capture(credential_bft_url + "/v1/bft/credentials/" + quote(shared_id, safe=""))
    finally:
        subprocess.run(["docker", "start", project + "-credential-api-4-1"], check=True)
        for attempt in range(60):
            ready = subprocess.run(["curl", "--fail", "--silent", credential_hosts[3] + "/q/health/ready"], capture_output=True)
            if ready.returncode == 0:
                break
            import time
            time.sleep(2)
        else:
            raise RuntimeError("Credentials host 4 did not recover after fault probe")
    quorum = credential_bft_one_fault[4].get("quorum", {})
    sources = credential_bft_one_fault[4].get("sources", [])
    if credential_bft_one_fault[4].get("result") != credential_host_results[0][4] or quorum.get("agreement") != "3-of-4" or len(sources) != 4 or sources[3].get("outcome") not in ("timeout", "unavailable"):
        raise RuntimeError("Live stopped-host proof failed")
bft = capture(os.environ.get("BFT_READER_URL", "http://localhost:42007").rstrip("/") + "/v1/bft/dids/" + quote(did_id, safe=""))
if bft[4].get("quorum", {}).get("agreement") != "4-of-4":
    raise RuntimeError("BFT reader did not agree 4-of-4")
error_url = credential_url + "/v1/credentials/" + quote("urn:demo:absent", safe="")
missing = capture(error_url, "404")
if missing[4].get("error") != "credential not found":
    raise RuntimeError("Missing credential error did not match contract")
if did_id != "did:canton:" + dso:
    raise RuntimeError("DID admin mismatch")
services = did_document[4]["didDocument"]["service"]
lines.extend(["## DID discovery and resolution", "", f"Registry admin and DID DSO: `{dso}`; distinct issuer: `{issuer}`.", ""])
for label, result in [("DID document", did_document), ("DID alias assertion", rows[9][3])]:
    # The same response is already shown above; retain one body only.
    lines.extend([f"{label}: verified HTTP {result[1]} at `{result[0][-1]}`; full response in Credentials / VC above.", ""])
forward = next(item for item in services if item["id"] == did_id + "#name-resolution")
reverse = next(item for item in services if item["id"] == did_id + "#reverse-name-resolution")
probes = []
for service, kind, path in [(forward, "CantonDemoNameResolutionService", "resolve"), (reverse, "CantonDemoReverseNameResolutionService", "reverse-resolve")]:
    endpoints = service["serviceEndpoint"]["endpoints"]
    if service["type"] != kind or service["serviceEndpoint"]["version"] != "1.0" or len(endpoints) != 4:
        raise RuntimeError(f"Invalid advertised {kind} service")
    for index, endpoint in enumerate(endpoints):
        advertised = f"http://localhost:{42003 + index}/v1/{path}"
        if endpoint["priority"] != index or endpoint["uri"] != advertised:
            raise RuntimeError(f"Incorrect advertised {kind} endpoint: {endpoint}")
        # Override published host ports only when running an isolated project.
        host_port = int(os.environ.get(f"DID_API_{index + 1}_PORT", str(42003 + index)))
        transport = f"http://localhost:{host_port}/v1/{path}"
        params = {"network": "localnet", "source": "did"}
        params.update({"name": "alice.canton"} if path == "resolve" else {"party": alice})
        result = capture(transport + "?" + urlencode(params))
        body = result[4]
        if (body.get("party_id") != alice if path == "resolve" else not isinstance(body, list) or len(body) != 1 or body[0].get("party_id") != alice):
            raise RuntimeError(f"DID proxy did not resolve Alice: {transport}")
        probes.append((advertised, result))
        lines.extend([f"### DID proxy {index + 1} {path}", "", f"Objective: verify advertised `{advertised}` (priority {index}) forwards through its corresponding Credentials host and agrees with the other three proxies.", "Does not prove: an independent name authority or a DID proxy quorum.", "", "```sh", shlex.join(result[0]), "```", f"HTTP status: {result[1]}; Content-Type: {result[2]}; curl exit code: {result[3]}", "", "```json", json.dumps(body, indent=2, ensure_ascii=False), "```", ""])
for path in ("resolve", "reverse-resolve"):
    matches = [result for url, result in probes if url.endswith("/" + path)]
    if any(result[4] != matches[0][4] for result in matches[1:]):
        raise RuntimeError(f"DID proxies disagree on {path}")
    lines.extend(["", f"Four {path} bodies equal by parsed JSON; full live bodies and exact curl commands above.", ""])
for index in range(4):
    base = f"http://localhost:{os.environ.get(f'DID_API_{index + 1}_PORT', 42003 + index)}"
    for name, expected in [("bob.canton", "409"), ("unclaimed.canton", "404")]:
        result = capture(base + "/v1/resolve?" + urlencode({"name": name, "network": "localnet", "source": "did"}), expected)
        rendered = json.dumps(result[4], indent=2, ensure_ascii=False)
        if json.loads(rendered) != json.loads(result[5]) or ("\n" not in rendered and rendered not in ("[]", "{}")) or any((len(line) - len(line.lstrip(" "))) % 2 for line in rendered.splitlines()):
            raise RuntimeError("DID conflict/missing formatting failed")
        lines.extend([f"### DID proxy {index + 1} {name} ({expected})", "", "Objective: observe the conflict or unallocated-name response from this DID proxy.", "Does not prove: name ownership outside this seeded localnet.", "", "```sh", shlex.join(result[0]), "```", f"HTTP status: {result[1]}; Content-Type: {result[2]}; curl exit code: {result[3]}", "", "```json", rendered, "```", ""])
lines.extend(["", "## Topology and SQL / check summary", "", "The registry and registered credential share the DSO/PQS projection with DID. The BFT reader checks DID document digest quorum, not independent name authority. Four proxies returned the same forward and reverse responses; conflicting Bob returned 409, unallocated name returned 404 on each. PQS SQL is exercised through the registry, credentials, registered credential, DID, alias and resolution readers; no SQL query plan or independent database audit is claimed.", ""])
lines.extend(["## Four Credentials hosts and Credentials BFT reader", "", "Objective: read the same seeded VC2 credential from four HTTP hosts and compare full parsed JSON, then read it through the Credentials BFT reader.", "Does not prove: independent registries or the full fault matrix. All hosts query the same PQS; the reader fault matrix is unit-level only (`CredentialBftReaderTest`). The healthy live reader path has four agreeing origins; the optional stopped-host probe demonstrates only one real availability fault.", "", "Four Credentials host bodies equal by parsed JSON; one logical registry backed by the same PQS. DID hosts 42003-42006 each forward to their corresponding Credentials host. The DID proxy equality checks above exercise all four forwarding paths.", ""])
for label, result in [(f"Credentials host {index} (port {41002 + index})", result) for index, result in enumerate(credential_host_results, 1)] + [("Credentials BFT healthy 4-of-4", credential_bft), ("Credentials BFT missing credential (404)", credential_bft_missing)]:
    command, status, content_type, exit_code, body, raw_body = result
    rendered = json.dumps(body, indent=2, ensure_ascii=False)
    if json.loads(rendered) != json.loads(raw_body) or ("\n" not in rendered and rendered not in ("[]", "{}")) or any((len(line) - len(line.lstrip(" "))) % 2 for line in rendered.splitlines()):
        raise RuntimeError(f"Response failed raw-body/indentation validation: {label}")
    lines.extend([f"### {label}", "", f"Objective: {'Verify the same credential at this host.' if 'host' in label else 'Observe the reader status and full body for a seeded or absent credential.'}", "Does not prove: independent data stores or a fault-tolerant live read.", "", "```sh", shlex.join(command), "```", f"HTTP status: {status}; Content-Type: {content_type}; curl exit code: {exit_code}", "", "```json", rendered, "```", ""])
if credential_bft_one_fault is not None:
    command, status, content_type, exit_code, body, raw_body = credential_bft_one_fault
    rendered = json.dumps(body, indent=2, ensure_ascii=False)
    if json.loads(rendered) != json.loads(raw_body) or "\n" not in rendered or any((len(line) - len(line.lstrip(" "))) % 2 for line in rendered.splitlines()):
        raise RuntimeError("Fault proof formatting failed")
    lines.extend(["### Credentials BFT with credential-api-4 stopped", "", "Objective: show an actual live 3-of-4 successful read when credential-api-4 is stopped, with the fourth origin unavailable.", "Does not prove: tolerance of two faults or Byzantine content. This is one stopped-container availability fault, not the unit-level full fault matrix.", "", "```sh", shlex.join(command), "```", f"HTTP status: {status}; Content-Type: {content_type}; curl exit code: {exit_code}", "", "```json", rendered, "```", ""])
for label, result in [("Registry", registry), ("Registered credential (VC2)", registered), ("BFT DID quorum", bft), ("Missing credential (404)", missing)]:
    command, status, content_type, exit_code, body, raw_body = result
    rendered = json.dumps(body, indent=2, ensure_ascii=False)
    if json.loads(rendered) != json.loads(raw_body) or ("\n" not in rendered and rendered not in ("[]", "{}")) or any((len(line) - len(line.lstrip(" "))) % 2 for line in rendered.splitlines()):
        raise RuntimeError(f"Response failed raw-body/indentation validation: {label}")
    lines.extend([f"### {label}", "", "```sh", shlex.join(command), "```", f"HTTP status: {status}; Content-Type: {content_type}; curl exit code: {exit_code}", "", "```json", rendered, "```", ""])
output.write_text("\n".join(lines).rstrip("\n") + "\n", encoding="utf-8")
print(f"Wrote {output}: 13 scenarios, 8 successful DID proxy calls, 8 conflict/missing probes, 4 unique shared checks")
PY
