# Independent Credentials Canton/PQS demo

This non-normative vertical slice runs an independent Credentials stack. It owns its Canton process, PostgreSQL database, PQS pipeline, artifact volume, network, seed job, projection waiter, and Java API. It does not use DID files or resources.

The Canton Open Source 3.5.11 container is a minimal local Canton synchronizer: one in-memory `credential-participant`, one local `credential-sequencer`, and one `credential-mediator` in a single process. It is neither an SV nor official LocalNet.

## Versions and artifacts

The `interface`, `model`, and `test` packages use DPM/Daml SDK 3.5.11 and Daml-LF 2.2. The artifact image contains exactly:

- `canton-network-credentials-interfaces-0.1.0.dar`
- `canton-network-credentials-demo-0.1.0.dar`
- `canton-network-credentials-demo-scripts-0.1.0.dar`

Only the interface and model DARs are uploaded. The test DAR is used only by `credential-seed`, which creates 360 registered credentials. PQS is pinned to 3.5.8 and projects this participant's ledger into its own PostgreSQL database.

## Ports and concurrent operation

Container ports are stable service-local ports. Published host ports can be overridden:

| Endpoint | Internal | Default host | Variable |
| --- | ---: | ---: | --- |
| PostgreSQL | `5432` | `15432` | `POSTGRES_PORT` |
| Ledger API | `6865` | `41000` | `LEDGER_PORT` |
| Canton HTTP API and `/livez` | `7575` | `41001` | `CANTON_HTTP_PORT` |
| PQS health | `8080` | `41002` | `PQS_HEALTH_PORT` |
| Credentials API | `8080` | `41003` | `CREDENTIALS_API_PORT` |

PostgreSQL remains available to containers at `credential-postgres:5432`. From desktop pgAdmin or another host SQL client, connect to `localhost:15432` by default, or use the host port set by `POSTGRES_PORT`. Compose gives each demo a separate Docker network, so both can reuse the standard internal ports and service DNS names without conflict. Host mappings are machine-wide, however, and must be distinct to run the Credentials and DID demos concurrently. The project name is `cn-credential-demo`; Compose creates project-scoped `credential-network`, `credential-artifacts`, and `credential-postgres-data` resources.

## Lifecycle

```sh
make build
make up
make smoke-test
make down
```

`make down` removes only this project's containers and network and preserves data. `make reset` requires the exact confirmation `RESET-CREDENTIAL-DEMO` and removes only this project's volumes as well.

For a slow manual startup with seeding intentionally deferred:

```sh
docker compose up --build credential-artifacts credential-postgres credential-canton
docker compose up credential-dar-upload
docker compose up --no-deps -d credential-pqs
docker compose run --rm credential-seed
docker compose run --rm credential-projection-waiter
docker compose up -d credential-api
./scripts/smoke-test.sh
```

PQS starts from Genesis, so starting it before the seed is safe. In the normal graph it starts after the seed and replays from Genesis.

Other useful targets are `make daml-build`, `make daml-test`, `make java-test`, `make seed`, `make projection-check`, `make ps`, and the domain-specific log targets.

## API

- `GET /v1/credentials/{credentialId}`
- `GET /v1/credentials?page=0&pageSize=50`
- `GET /v1/registered-credentials/{credentialId}`
- `GET /v1/registered-credentials?page=0&pageSize=50`
- `GET /v1/credential-registries?page=0&pageSize=50`
- `GET /v1/credential-registries/{registryId}`
- `GET /q/health/live`
- `GET /q/health/ready`

The API reads only PQS's documented `active(...)` interface surface through prepared JDBC statements. It exposes active records only and does not fabricate inactive records or history.
