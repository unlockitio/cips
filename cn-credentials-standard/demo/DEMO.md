# Four-minute stakeholder walkthrough

Run from `cn-credentials-standard/demo` after a fresh, fully seeded unified stack is warm. Canton startup takes roughly 5–6 minutes, so do not start it during the presentation. Check `docker compose -p cn-credential-demo -f compose.yaml ps` and run `bash scripts/demo.sh --check` first. The check requires healthy Credentials, DID and Credentials BFT endpoints and the new 370-projection seed; it performs reads only and never stops a container. The interactive `bash scripts/demo.sh` pauses for Enter at each step. `jq` filters output for presentation only. For full HTTP response bodies and reproducible status checks use `scripts/per-cip-curls.sh` during a separately authorized live capture, not during CP1.

| Time | Step | Point to make |
| --- | --- | --- |
| 0:00–0:35 | Party-issued `urn:demo:credential:000` | Real DemoIssuer Party appears in anchorers: ledger authorization is visible. |
| 0:35–1:35 | `external-did`, then `external-holderless` | JWT envelope ID begins `data:application/vc+jwt,`; embedded proof names `eddsa-jcs-2022`. Separate POSTs verify both demo-key proofs and local schemas; the GETs only carry secured material anchored by DemoAnchorer. |
| 1:35–2:10 | `external-tampered` | Score 86 matches the typed projection but the test-key signature covered score 85 for this same ID: Standalone POST reports `FAILED` signature while schema remains `Success`; GET has no calculated result. |
| 2:10–3:10 | Stop credential-api-4, BFT GET, restore | Three matching HTTP origins still return 200 with `3-of-4`. Script traps exit and starts host 4, waiting for readiness before completion. This is one stopped-host availability test. |
| 3:10–4:00 | DSO DID discovery on 42003 | Runtime DSO DID document advertises the credential name-resolution service and self-claimed `urn:fqpn:localnet:demo:dso.canton` alias. |

Prerequisites: Docker Compose, curl, jq, fresh volumes when relaunching the in-memory Canton participant, and all four Credentials APIs healthy before the fault step. Run the walkthrough only with explicit live-operation approval; `--check` is safe and non-disruptive but requires an already running, correctly seeded stack. The stop/restore uses project `cn-credential-demo` and this directory's `compose.yaml`, with `up -d --no-deps credential-api-4` for restoration so no Canton or PQS dependency is recreated. If the restore readiness check fails, investigate the container before claiming recovery.

Do not claim W3C conformance, general interoperability, remote trust, independent BFT fault domains, or production-key security. The Ed25519 key is deterministically derived from a public TEST ONLY label. Canton accepts the tampered record structurally; the consumer fails cryptographic verification off-ledger. Schema success is independent of issuer authenticity. The DSO alias is a self-claim, not allocated name ownership. The four APIs share one PQS and one Canton participant.

Optional backup recording for the presenter: check `command -v asciinema` first. If installed, record an explicitly authorized interactive walkthrough with `asciinema rec` and run `bash scripts/demo.sh` inside that session. Keep recordings local unless their publication is separately approved. Do not install tools or capture live evidence during offline code validation.
