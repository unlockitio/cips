# Credential Registry PQS demo

This non-normative demo exercises the candidate credential interfaces with one local Canton sandbox, Participant Query Store (PQS), PostgreSQL, and a Java 21/Javalin API. PQS owns continuous ledger indexing. The API reads only the documented PQS SQL reader surface and never falls back to the Ledger API.

## Prerequisites and pinned components

- Docker with Compose v2
- Daml SDK `3.4.0-rc2`
- Java 21 and Maven 3.9.x for host builds, or Docker for the API build
- `curl`
- `jq`

Pinned containers:

- `digitalasset/daml-sdk:3.4.0-rc2`
- `europe-docker.pkg.dev/da-images/public/docker/participant-query-store:3.4.1`
- `postgres:16.6-alpine`
- `maven:3.9.9-eclipse-temurin-21`
- `eclipse-temurin:21-jre`

The Digital Asset images may be subject to registry access and license terms. The 3.4.1 PQS image was inspected for its real command-line options. Compatibility with the `3.4.0-rc2` LF 2.1 package must still be established by a successful full run; do not infer production compatibility from matching major/minor versions.

## Run

```sh
cd cn-credentials-standard/demo
cp .env.example .env
make build
make up
make daml-test
make seed
make smoke-test
make down
```

`make down` stops containers without deleting the named PostgreSQL volume. This project provides no destructive volume-removal target.

## Validation status

The in-process Daml seed/test verifies the 360-credential fixture and lifecycle behavior. A partial live PQS run verified 36 `Credential` and 36 `RegisteredCredential` projections. A fresh full 300+ live validation was blocked by intermittent Canton synchronizer readiness with `PACKAGE_SERVICE_CANNOT_AUTODETECT_SYNCHRONIZER`. The complete 360-record PQS/API smoke-test path above is therefore the intended full-demo path, not fully live-validated evidence.

## Architecture and data flow

1. Canton starts with the compiled demo DAR.
2. `seed.sh` waits for the Ledger API, allocates `DemoIssuer`, `DemoHolderA`, `DemoHolderB`, and `DemoRegistryAdmin`, then submits commands as the allocated issuer Party ID.
3. The concrete template uses the issuer as its stable demo-only signatory, with all holders and the registry admin as observers. Its `authorityParty` field identifies the current holder authority for the fixture and moves to a remaining holder when necessary. The stable issuer signatory lets any holder's self-removal choice create the replacement while preserving logical data and registration metadata. The registry admin can see every demo credential. This authority model is deliberately non-normative and is not an issuer-governance recommendation.
4. PQS continuously consumes visible ledger updates and stores document projections in PostgreSQL.
5. The API joins active `Credential` and `RegisteredCredential` interface projections by contract ID through `active(...)` with prepared JDBC statements. Intrinsic credential fields and registration metadata are separate interface payloads. Daml sum types such as `W3C_VC_Identifier` remain JSON objects in the HTTP response rather than being coerced to strings.

Real Super Validator or organization names are intentionally absent. Names such as `DemoIssuer` are allocation hints for neutral synthetic parties and imply no endorsement, authority, or known sandbox Party ID.

## Seed data

The script creates 360 active credentials with stable logical IDs from `urn:demo:credential:000` through `urn:demo:credential:359`. Seed `20260918` drives deterministic fixture variation across four credential categories, optional subject identifiers, claims, and one/two-holder combinations.

The logical IDs are deliberately repeated by another seeding run. Use a fresh demo volume for a clean fixture set. A repeated run demonstrates the lookup API's duplicate protection and is not silently made idempotent by writing to PQS.

## API

- `GET /health/live` is dependency-independent process liveness.
- `GET /health/ready` returns 200 only after an interface-filtered PQS `active(...)` query executes, otherwise 503 with a JSON reason.
- `GET /api/v1/credentials/{credentialId}` returns 200 for one logical-ID match, 404 for none, and 409 for duplicates.
- `GET /api/v1/credentials?page=0&pageSize=50` returns `items`, zero-based `page`, `pageSize`, and `hasNext`.

Examples:

```sh
curl http://localhost:8080/health/ready
curl http://localhost:8080/api/v1/credentials/urn:demo:credential:042
curl 'http://localhost:8080/api/v1/credentials?page=2&pageSize=100'
```

The default page size is 50 and maximum is 100. Negative pages, page sizes outside 1 to 100, non-integers, and offset overflow are client errors. Queries order by logical credential ID and contract ID and request `pageSize + 1` rows.

PQS visibility is the visibility of its configured ledger identity and filters. Its rows are eventually consistent with Canton. Readiness polling uses an executable PQS SQL query rather than a fixed delay. Offset pagination is intentionally simple: concurrent creates, archives, or replacements can shift boundaries, so cross-page reads may contain duplicates or omissions and are not snapshot-consistent.

## Security and limitations

This demo contains public synthetic data and no production authentication or authorization. It publishes the unauthenticated Canton Ledger API and PQS health ports to the host for local seeding and troubleshooting; do not expose them beyond a local development environment. It is not a governance model, production registry, privacy profile, or endorsement. The API does not claim current-ledger confirmation beyond PQS-observed active state. Holder self-removal preserves registration metadata and creates one replacement; all-holder archival is jointly controlled as required by the candidate interface.

## Troubleshooting

```sh
docker compose ps
docker compose logs canton
docker compose logs pqs
docker compose logs api
```

If `/health/ready` remains unavailable, inspect PQS logs first, then inspect PostgreSQL functions without querying physical `__*` tables:

```sh
docker compose exec postgres psql -U pqs -d pqs -c "select n.nspname, p.proname, pg_get_function_identity_arguments(p.oid), pg_get_function_result(p.oid) from pg_proc p join pg_namespace n on n.oid=p.pronamespace where p.proname in ('active','lookup_contract','create_index_for_contract');"
```

See [`sql/README.md`](sql/README.md) for the verified optional payload index. `make demo-index` installs it through PQS's supported helper. Build products under `daml/.daml` and `java-api/target` are ignored. `make clean` uses Daml and Maven clean commands; it does not delete volumes.
