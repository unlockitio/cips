# Independent DID Canton/PQS demo

This non-normative demo owns a Canton Open Source 3.5.11 process (in-memory participant, sequencer and mediator), PostgreSQL, PQS 3.5.8, artifact volume, network, seed and projection jobs, four identical Java source APIs, and a separate Java 21/Quarkus BFT reader. It does not use Credentials resources and is neither an SV nor official LocalNet.

## Topology and limits

| Endpoint | Internal | Default host | Variable |
| --- | ---: | ---: | --- |
| PostgreSQL | 5432 | 25432 | POSTGRES_PORT |
| Ledger API | 6865 | 42000 | LEDGER_PORT |
| Canton HTTP API | 7575 | 42001 | CANTON_HTTP_PORT |
| PQS health | 8080 | 42002 | PQS_HEALTH_PORT |
| DID API 1 | 8080 | 42003 | DID_API_1_PORT |
| DID API 2 | 8080 | 42004 | DID_API_2_PORT |
| DID API 3 | 8080 | 42005 | DID_API_3_PORT |
| DID API 4 | 8080 | 42006 | DID_API_4_PORT |
| BFT reader | 8080 | 42007 | BFT_READER_PORT |

Instances identify as `did-api-1` through `did-api-4`. All use the same PQS/PostgreSQL projection. The reader has no JDBC or PQS access. Its exact origin map maps localhost ports 42003, 42004, 42005, 42006 to those four internal services on port 8080. In particular, `localhost:42006` maps to `http://did-api-4:8080`. Reader port 42007 is never advertised as a source.

Fixed membership is four, with three matching comparable responses required regardless of reachability. Success reports `3-of-4` or `4-of-4`, canonical SHA-256 digest, all four ordered source diagnostics and authority details. Selection uses priority then URI, never completion order. Canonicalization sorts object keys, preserves array order and scalar types, and excludes replica-local `instanceId` fields.

This tolerates one API-level unavailable or arbitrary divergent response when the other three agree, provided trusted bootstrap discovery is available. Bootstrap still uses `BFT_DID_REGISTRY_URL`, defaulting to API 1; losing that bootstrap yields 502 before voting. Shared PQS/Postgres, unauthenticated responses, and absence of finality/freshness proofs mean this is not an independently certified BFT deployment. Matching responses are not proof of authoritative truth.

## Seed and migration

The seed allocates or recovers the local Party with hint `DSO`. Its full runtime Party string is prefixed with `did:canton:` and retained as the document ID and sole controller. It is stored raw and percent encoded only as a URL path segment. A supplied mainnet identifier is never substituted.

Each of six services advertises exactly four ordered URLs on localhost ports 42003, 42004, 42005, 42006 with priorities 0, 1, 2, 3, for 24 URLs:

| Fragment/root under `/v1/` | Type |
| --- | --- |
| did-registries | CantonDidRegistryService |
| dids | CantonDidResolutionService |
| credential-registries | CantonCredentialRegistryService |
| credentials | CantonCredentialService |
| asset-registries | CantonAssetRegistryService |
| assets | CantonAssetService |

Daml verification methods, authentication, assertionMethod and capabilityInvocation remain empty. API JSON emits `verificationMethod` and all five verification relationship arrays (including keyAgreement and capabilityDelegation) as `[]`, never null or omitted. Registration, anchoring and public-fetch semantics are unchanged.

Seeding is create-if-absent, NOT upsert: a rerun returns the existing contract ID without updating its document; duplicates abort. Existing three-endpoint contracts therefore remain stale and are rejected by four-endpoint discovery. No ledger archive/update migration is implemented. For disposable local data only, explicitly choose a clean local reset/reseed with `make reset` (requires `RESET-DID-DEMO`), followed by `make build` and `make up`. This deletes that project's data; never use it against data you need to retain. Alternatively start a uniquely named isolated project with fresh project volumes and unused host ports. No reset is performed automatically. Daml tests cover fresh seed and unchanged rerun identity/document.

## Build and lifecycle

The interface, model and test packages use DPM/Daml SDK 3.5.11 and LF 2.2. Artifacts are `canton-network-did-interfaces-0.1.0.dar`, `canton-network-did-demo-0.1.0.dar`, and `canton-network-did-demo-scripts-0.1.0.dar`. Only interface/model DARs are uploaded; the scripts DAR is used by seeding.

```sh
make build
make up
make smoke-test
make down
```

The default project is `cn-did-demo`. `make down` preserves volumes. For isolation use explicit `docker compose -p <unique-name>` commands and override every published host port, including API 4. Advertised source identities remain the fixed demo ports; internal routing uses the exact origin map.

Useful targets: `daml-build`, `daml-test`, `java-test`, `bft-reader-test`, `bft-reader-fault-e2e`, `bft-reader-e2e`, `seed`, `projection-check`, `ps`, and log targets.

## APIs and reader contract

`GET /v1/dids` and `/v1/dids/{canonical encoded DID}` expose intrinsic documents. `/v1/registered-dids`, its DID lookup and `?partyId=` filter expose registration separately. Singular JDBC reads use `active(...)`; direct collections use PQS 3.5 `active(name, offset)`, `creates(name, 0, offset)`, and `archives(name, 0, offset)` with inclusive bigint bounds. `state=active|archived|all` and ISO-8601 `createdFrom`/`archivedFrom` (inclusive, `>=`) and `createdUntil`/`archivedUntil` (exclusive, `<`) select lifecycle records. Intervals with `From >= Until` are invalid; either archive filter excludes active rows. Tokens bind normalized filters using these canonical Until names. Collection items add `lifecycle` event IDs, indexes, offsets, template/payload identity and effective timestamps. Archive fields are null when active at the snapshot; registration deactivation is separate. Registered joins match contract and creation events, plus archive events for archived reads. No history is fabricated. The other five advertised roots return only `{family, apiVersion, instanceId, status: "discovery-only"}`, not credential or asset domain implementations.

The only reader operation is `/v1/bft/dids/{canonical encoded DID}`. Collections are direct single-PQS reads, not quorum operations. Bootstrap uses API 1's singular `/v1/dids/dso` response rather than a collection envelope. Discovery requires one full local DSO DID and the exact six-service/four-endpoint structure. Redirects, credentials, fragments, unknown origins/paths, duplicate endpoints, malformed JSON, non-JSON content and bodies over 1 MiB are rejected. Connect/read timeouts default to 2 seconds and overall deadline to 3 seconds.

Outcomes: 400 invalid input, 404 at least three matching not-found responses, 409 four comparable responses without a three-vote digest quorum (including 2+2), 502 untrusted/failed bootstrap, 503 insufficient agreement with unavailable/non-comparable sources, 504 overall deadline. Two matches plus conflict plus unavailable never succeed. See `bft-reader/openapi.yaml`.

Fresh collection requests select `set_latest(NULL)` and validate the chosen offset. Responses contain `snapshotOffset`, `nextPageToken`, `page`, `pageSize`, `hasNext` and items, without `observedAt`. With no token, optional `page` selects a fresh snapshot page. Token alone uses DID/contract keyset continuation; token plus explicit `page` jumps by OFFSET within that snapshot. Repeat filters/page size identically or omit them. Page size defaults to 50, maximum 100. The next token is null at the end; retain an earlier token for snapshot page jumps.

Set `DID_LISTING_HMAC_SECRET` to a random secret of at least 32 bytes shared by replicas. Compose's explicit local-demo default is not a deployment secret. `DID_LISTING_TOKEN_TTL_SECONDS` defaults to 900; continuation does not renew expiry. HMAC-SHA256 verification uses a constant-time MAC comparison. Invalid/tampered, expired, wrong-resource, changed-query and unavailable-snapshot errors are distinct. Key rotation invalidates outstanding tokens. Readiness exercises the actual public historical signatures and columns, so incompatible PQS installations fail instead of falling back to current reads. `/v1/dids/capabilities` reports single-PQS listings and singular-only BFT.

Bootstrap selects the sole active `did:canton:DSO::` candidate regardless of other intrinsic documents, or uses `DID_BOOTSTRAP_DSO_DID` as an exact configured DID. Zero or ambiguous candidates fail. No global singleton assumption remains.

## Fault and availability testing

Faults are disabled by default. `compose.bft-faults.yaml` supports API 1 through 4 with per-instance `DID_API_N_FAULTS_ENABLED`, `DID_API_N_FAULT_MODE`, `DID_API_N_FAULT_FAMILY`, and `DID_API_N_FAULT_DELAY`. Modes: normal, semantic-mismatch, malformed-json, http-500, delay, unavailable. Faults do not mutate ledger or database state. The reader only compares singular DID responses. The real HTTP component harness isolates bootstrap from injected response faults; deployment-level DID faults may also affect the bootstrap and return 502 before voting.

`make bft-reader-fault-e2e` runs real HTTP servers on ephemeral loopback ports, without Docker: four healthy matches; one semantic mismatch, malformed response, HTTP 500 or timeout with three matches; 2+2 conflict; two matches plus conflict and unavailable; insufficient comparable sources; and three matching 404s. Separately labeled crash/availability checks stop one HTTP server for 3-of-4 success and two for 503. Taking servers down is not Byzantine testing.

`scripts/bft-reader-e2e.sh` checks healthy 4-of-4 singular DID resolution and rejection of removed collection routes. Smoke checks four APIs, six services, 24 source URLs and empty verification arrays. Full Compose healthy/fault/crash orchestration must use a unique project and unused ports, never the user's default stack.
