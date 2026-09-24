# PQS SQL and optional indexes

The demo API uses the documented PQS `active(...)` reader for current active projections in the Credentials stack's private PostgreSQL 18.6 database; its queries select only credential interfaces and do not query physical `__*` tables. PostgreSQL is published to the host at `localhost:15432` and is reachable between containers on `credential-network` at `credential-postgres:5432`. The demo's separate Docker network isolates this internal endpoint; only the distinct `41000`-`41003` host mappings need to avoid the DID demo's published range when both run concurrently. Physical tables are PQS implementation details and are not a stable reader API. Current evidence does not establish a stable PQS SQL reader for inactive or archived projections, so the demo does not advertise inactive-record or event-history capabilities. A conforming implementation that advertises either capability must document its implementation-specific backing; event history uses the separate event operation and schema defined by the OpenAPI contract.

PQS 3.5.8 exposes the supported payload-index procedure with this installed signature:

```text
create_index_for_contract(
  IN name text,
  IN qname text,
  IN expression text,
  IN index_type text,
  IN index_opclass text
)
```

For this demo the narrow exact-ID index is:

```sql
CALL create_index_for_contract(
  'credential_id_idx',
  'canton-network-credentials-interfaces:Canton.Network.Credentials.V1:Credential',
  '(payload ->> ''id'')',
  'btree',
  'text_ops'
);
```

The expression and unprefixed qualified name are validated against the 3.5.8 container and a live interface payload. The registry collection first pages distinct `registryAdmin` values from `active('canton-network-credentials-interfaces:Canton.Network.Credentials.V1:CredentialRegistryFactory')`, ordered by the canonical Party string, and then reads every matching factory ordered by `issuer` and `contract_id`. This keeps all factories for one logical registry together and never uses a factory contract ID as registry identity. The API combines the active `Credential` and `RegisteredCredential` projections by contract ID: the former carries intrinsic credential fields, while the latter carries registration metadata. `RegisteredCredential` requires `Credential`; a registered result is valid only when both projections identify the same canonical contract. Registered renewal replaces both projections atomically under one replacement contract ID. Inspect the documented reader surface directly with:

```sql
SELECT c.contract_id,
       c.payload AS credential_payload,
       r.payload AS registration_payload
FROM active('canton-network-credentials-interfaces:Canton.Network.Credentials.V1:Credential') c
JOIN active('canton-network-credentials-interfaces:Canton.Network.Credentials.V1:RegisteredCredential') r
  USING (contract_id)
LIMIT 1;
```

The PQS credential payload has top-level `id`, `issuer`, `credentialTypes`, `credentialSubject`, `holders`, `validFrom`, and `validUntil` fields. A W3C serialization maps `credentialTypes` to JSON-LD `type`; PQS retains the Daml field name. The RegisteredCredential payload has top-level `registryAdmin`, `registeredAt`, `expiresAt`, and `meta` fields. `validUntil` is intrinsic validity while `expiresAt` is registry retention; readers must keep them distinct even though the DSO profile sets them equal where both are present and renews them atomically. Natural expiry after `validUntil` creates no transaction. `issuer` is an encoded `W3C_VC_Identifier` object, not scalar text.

This particular procedure and PostgreSQL expression are optional implementation details. The standard instead requires logical indexes or equivalent access paths for exact credential ID, contract-ID association, deterministic credential-ID-plus-contract-ID ordering, and every advertised standard filter. Additional indexes are allowed. During an earlier partial live run with 36 projected credentials, an `EXPLAIN` of exact lookup used a bitmap scan over the generated expression index. The independent stack must be runtime-validated before release. No GIN index is installed because the demo API has no containment query.

To inspect the installed reader/helper contract without relying on internal tables:

```sql
SELECT n.nspname,
       p.proname,
       pg_get_function_identity_arguments(p.oid),
       pg_get_function_result(p.oid)
FROM pg_proc p
JOIN pg_namespace n ON n.oid = p.pronamespace
WHERE p.proname IN ('active', 'lookup_contract', 'create_index_for_contract');
```
