# DID demo in the shared Canton network

The default launch is `make build && make up` from `cn-credentials-standard/demo`, not this directory. That Compose project runs one Canton Open Source 3.5.11 synchronizer and participant for both Credentials and DID, and the DID APIs there read the single shared PQS/PostgreSQL projection of that participant rather than a DID-specific one. The four identical Java source APIs and the separate Java 21/Quarkus BFT reader are unchanged. The Compose file here remains an optional isolated legacy DID stack with its own single Canton, PQS and PostgreSQL; running it alongside the default stack would start a second, unwanted Canton network. Neither topology is an SV or official LocalNet.

## Topology and limits

The table below describes this isolated standalone stack. In the unified default stack there is no DID-specific PostgreSQL or PQS health port; the DID APIs read `credential-postgres:5432` (host `15432`) fed by the one `credential-pqs` pipeline, and only the DID API and BFT reader rows apply.

| Endpoint | Internal | Default host | Variable |
| --- | ---: | ---: | --- |
| DID PostgreSQL (standalone only) | 5432 | 25432 | POSTGRES_PORT |
| Ledger API (standalone `42000`; unified `41000`) | 6865 | 42000 | LEDGER_PORT |
| Canton HTTP API (standalone `42001`; unified `41001`) | 7575 | 42001 | CANTON_HTTP_PORT |
| DID PQS health (standalone only) | 8080 | 42002 | PQS_HEALTH_PORT |
| DID API 1 | 8080 | 42003 | DID_API_1_PORT |
| DID API 2 | 8080 | 42004 | DID_API_2_PORT |
| DID API 3 | 8080 | 42005 | DID_API_3_PORT |
| DID API 4 | 8080 | 42006 | DID_API_4_PORT |
| BFT reader | 8080 | 42007 | BFT_READER_PORT |

Instances identify as `did-api-1` through `did-api-4`. All four read one PQS/PostgreSQL projection, which in the unified default stack is the same database the Credentials API reads. The reader has no JDBC or PQS access. Its exact origin map maps localhost ports 42003, 42004, 42005, 42006 to those four internal services on port 8080. In particular, `localhost:42006` maps to `http://did-api-4:8080`. Reader port 42007 is never advertised as a source.

Fixed membership is four, with three matching comparable responses required regardless of reachability. Success reports `3-of-4` or `4-of-4`, canonical SHA-256 digest, all four ordered source diagnostics and authority details. Selection uses priority then URI, never completion order. Canonicalization sorts object keys, preserves array order and scalar types, and excludes replica-local `instanceId` fields.

This tolerates one API-level unavailable or arbitrary divergent response when the other three agree, provided trusted bootstrap discovery is available. Bootstrap still uses `BFT_DID_REGISTRY_URL`, defaulting to API 1; losing that bootstrap yields 502 before voting. Shared PQS/Postgres, unauthenticated responses, and absence of finality/freshness proofs mean this is not an independently certified BFT deployment. Matching responses are not proof of authoritative truth.

## Seed and migration

The seed allocates or recovers the local Party with hint `DSO`. Its full runtime Party string is prefixed with `did:canton:` and retained as the document ID and sole controller. It is stored raw and percent encoded only as a URL path segment. A supplied mainnet identifier is never substituted.

Each of the six core services advertises exactly four ordered URLs on localhost ports 42003, 42004, 42005, 42006 with priorities 0, 1, 2, 3, for 24 URLs:

| Fragment/root under `/v1/` | Type |
| --- | --- |
| did-registries | CantonDidRegistryService |
| dids | CantonDidResolutionService |
| credential-registries | CantonCredentialRegistryService |
| credentials | CantonCredentialService |
| asset-registries | CantonAssetRegistryService |
| assets | CantonAssetService |

In the unified stack, two further demo-scoped services advertise four ordered DID-host endpoints each, on ports 42003 through 42006. Each DID API forwards only the two name routes to the single internal Credentials API (`CREDENTIALS_RESOLUTION_URL=http://credential-api:8080`). Name resolution originally lived exclusively on Credentials port 41003 while the DID APIs were replicas; 41003 remains directly callable. The standalone DID seed omits both services because that stack has no Credentials API; its unconfigured proxy routes return 503.

| Fragment/root under `/v1/` | Type |
| --- | --- |
| name-resolution | CantonDemoNameResolutionService |
| reverse-name-resolution | CantonDemoReverseNameResolutionService |

These are demo-only service types, not DID Core or CIP service requirements. Four HTTP front doors do not imply four resolver authorities, a BFT name quorum, or four Canton networks: all use the same Credentials allocation authority and one shared PQS. An advertised URL is not itself proof of current reachability or correctness. If Credentials is down, DID readiness remains independent and these proxy GETs return 503.

The seed also records one self-claimed name alias for the DSO's own Party, and owners may claim further aliases through `DemoDidDocument_AssertAliases`. An alias is an explicitly self-claimed discovery hint, never a unique allocation or proof of ownership: duplicate aliases across documents and claims about other Parties are possible by construction, and the Credentials name registry remains the only allocation authority. Aliases live on the concrete demo template rather than the candidate `DidDocumentView`. The demo Java API joins that exact active contract in PQS and projects only the seeded DSO Party's `urn:fqpn:localnet:demo:dso.canton` self-claim as `didDocument.alsoKnownAs` on intrinsic and registered records, including historical collection items from the matching create/archive event. The array is deterministic, de-duplicated and empty if the claim or template is absent; a mismatched joined document/owner fails closed. Alice's other-subject assertion and Bob's conflicting self-claim are not projected. This is a demo-specific allowlist, NOT proof of allocation: the name registry is not joined, so callers must cross-check it before trusting ownership. Existing contracts are not rewritten; a stale pre-alias contract returns `[]` until explicitly migrated or a disposable environment is reseeded. `GET /v1/demo-name-assertions?alias=<urn:fqpn URI>` or `?subject=<Party>` performs a bounded, indexed lookup of at most ten matches and never scans the DID collection. The canonical alias value is `urn:fqpn:<network>:<resolver>:<name>`, carrying the CIP #171 fully qualified party name as a URI because DID Core `alsoKnownAs` values are URIs rather than bare names.

Daml verification methods, authentication, assertionMethod and capabilityInvocation remain empty. API JSON emits `verificationMethod` and all five verification relationship arrays (including keyAgreement and capabilityDelegation) as `[]`, never null or omitted. Registration, anchoring and public-fetch semantics are unchanged.

The default seed runs after Credentials on the same participant, allocates or recovers DSO and checks the participant's own DemoHolderA Party before asserting Alice's alias. It is not importing a Party string from another ledger. Aliases remain self-claims, not cryptographic verification or registry authority. Seeding is create-if-absent, NOT upsert: a rerun returns the existing contract ID without updating its document; duplicates and deployment service-profile mismatches abort. Existing three-endpoint contracts therefore remain stale and are rejected by four-endpoint discovery. No ledger archive/update migration is implemented. For disposable default data only, explicitly choose a clean reset/reseed from `cn-credentials-standard/demo` with `make reset` (requires `RESET-CREDENTIAL-DEMO`), followed by `make build` and `make up`. The isolated legacy project's `make reset` requires `RESET-DID-DEMO` and affects only its own volumes. This deletes that project's data; never use it against data you need to retain. Alternatively start a uniquely named isolated project with fresh project volumes and unused host ports. No reset is performed automatically. Daml tests cover fresh seed and unchanged rerun identity/document.

## Build and lifecycle

The interface, model and test packages use DPM/Daml SDK 3.5.11 and LF 2.2. Artifacts are `canton-network-did-interfaces-0.1.0.dar`, `canton-network-did-demo-0.1.0.dar`, and `canton-network-did-demo-scripts-0.1.0.dar`. Only interface/model DARs are uploaded; the scripts DAR is used by seeding.

```sh
cd cn-credentials-standard/demo
make build
make up
make smoke-test
make down
```

From this DID demo directory, `cd ../../cn-credentials-standard/demo` instead. The default project is `cn-credential-demo`; this directory's `cn-did-demo` project is legacy and optional. `make down` preserves volumes. For isolation use explicit `docker compose -p <unique-name>` commands and override every published host port, including API 4. Advertised source identities remain the fixed demo ports; internal routing uses the exact origin map.

Useful targets: `daml-build`, `daml-test`, `java-test`, `bft-reader-test`, `bft-reader-fault-e2e`, `bft-reader-e2e`, `seed`, `projection-check`, `ps`, and log targets.

## APIs and reader contract

`GET /v1/dids` and `/v1/dids/{canonical encoded DID}` expose intrinsic documents. `/v1/registered-dids`, its DID lookup and `?partyId=` filter expose registration separately. Singular JDBC reads use `active(...)`; direct collections use PQS 3.5 `active(name, offset)`, `creates(name, 0, offset)`, and `archives(name, 0, offset)` with inclusive bigint bounds. `state=active|archived|all` and ISO-8601 `createdFrom`/`archivedFrom` (inclusive, `>=`) and `createdUntil`/`archivedUntil` (exclusive, `<`) select lifecycle records. Intervals with `From >= Until` are invalid; either archive filter excludes active rows. Tokens bind normalized filters using these canonical Until names. Collection items add `lifecycle` event IDs, indexes, offsets, template/payload identity and effective timestamps. Archive fields are null when active at the snapshot; registration deactivation is separate. Registered joins match contract and creation events, plus archive events for archived reads. No history is fabricated. The other five advertised roots return only `{family, apiVersion, instanceId, status: "discovery-only"}`, not credential or asset domain implementations.

The only reader operation is `/v1/bft/dids/{canonical encoded DID}`. Collections are direct single-PQS reads, not quorum operations. Bootstrap uses API 1's singular `/v1/dids/dso` response rather than a collection envelope. Discovery requires one full local DSO DID and the exact six-service/four-endpoint structure in the leading service positions; additional demo-scoped services beyond those six are ignored by the reader and are never used as voting sources. Redirects, credentials, fragments, unknown origins/paths, duplicate endpoints, malformed JSON, non-JSON content and bodies over 1 MiB are rejected. Connect/read timeouts default to 2 seconds and overall deadline to 3 seconds.

Outcomes: 400 invalid input, 404 at least three matching not-found responses, 409 four comparable responses without a three-vote digest quorum (including 2+2), 502 untrusted/failed bootstrap, 503 insufficient agreement with unavailable/non-comparable sources, 504 overall deadline. Two matches plus conflict plus unavailable never succeed. See `bft-reader/openapi.yaml`.

Fresh collection requests select `set_latest(NULL)` and validate the chosen offset. Responses contain `snapshotOffset`, `nextPageToken`, `page`, `pageSize`, `hasNext` and items, without `observedAt`. With no token, optional `page` selects a fresh snapshot page. Token alone uses DID/contract keyset continuation; token plus explicit `page` jumps by OFFSET within that snapshot. Repeat filters/page size identically or omit them. Page size defaults to 50, maximum 100. The next token is null at the end; retain an earlier token for snapshot page jumps.

Set `DID_LISTING_HMAC_SECRET` to a random secret of at least 32 bytes shared by replicas. Compose's explicit local-demo default is not a deployment secret. `DID_LISTING_TOKEN_TTL_SECONDS` defaults to 900; continuation does not renew expiry. HMAC-SHA256 verification uses a constant-time MAC comparison. Invalid/tampered, expired, wrong-resource, changed-query and unavailable-snapshot errors are distinct. Key rotation invalidates outstanding tokens. Readiness exercises the actual public historical signatures and columns, so incompatible PQS installations fail instead of falling back to current reads. `/v1/dids/capabilities` reports single-PQS listings and singular-only BFT.

Bootstrap selects the sole active `did:canton:DSO::` candidate regardless of other intrinsic documents, or uses `DID_BOOTSTRAP_DSO_DID` as an exact configured DID. Zero or ambiguous candidates fail. No global singleton assumption remains.

## Fault and availability testing

Faults are disabled by default. `compose.bft-faults.yaml` supports API 1 through 4 with per-instance `DID_API_N_FAULTS_ENABLED`, `DID_API_N_FAULT_MODE`, `DID_API_N_FAULT_FAMILY`, and `DID_API_N_FAULT_DELAY`. Modes: normal, semantic-mismatch, malformed-json, http-500, delay, unavailable. Faults do not mutate ledger or database state. The reader only compares singular DID responses. The real HTTP component harness isolates bootstrap from injected response faults; deployment-level DID faults may also affect the bootstrap and return 502 before voting.

`make bft-reader-fault-e2e` runs real HTTP servers on ephemeral loopback ports, without Docker: four healthy matches; one semantic mismatch, malformed response, HTTP 500 or timeout with three matches; 2+2 conflict; two matches plus conflict and unavailable; insufficient comparable sources; and three matching 404s. Separately labeled crash/availability checks stop one HTTP server for 3-of-4 success and two for 503. Taking servers down is not Byzantine testing.

`scripts/bft-reader-e2e.sh` checks healthy 4-of-4 singular DID resolution and rejection of removed collection routes. Smoke checks four APIs, six core services plus the two demo name-resolution services, 24 source URLs and empty verification arrays. Full Compose healthy/fault/crash orchestration must use a unique project and unused ports, never the user's default stack.
