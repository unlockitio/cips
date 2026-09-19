# PQS SQL and optional indexes

The API uses PQS reader functions, specifically `active(...)`, and does not query physical `__*` tables. Those tables are PQS implementation details and are not a stable reader API.

PQS 3.4.1 exposes the supported payload-index procedure with this installed signature:

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

The expression and unprefixed qualified name are validated against the 3.4.1 container and a live interface payload. The API combines the active `Credential` and `RegisteredCredential` projections by contract ID: the former carries intrinsic credential fields, while the latter carries registration metadata. Inspect the documented reader surface directly with:

```sql
SELECT c.contract_id,
       c.payload AS credential_payload,
       r.payload AS registration_payload
FROM active('canton-network-credentials-interfaces:Canton.Network.Credentials.V1:Credential') c
JOIN active('canton-network-credentials-interfaces:Canton.Network.Credentials.V1:RegisteredCredential') r
  USING (contract_id)
LIMIT 1;
```

The credential payload has top-level `id`, `issuer`, `credentialTypes`, `credentialSubject`, `holders`, `validFrom`, and `validUntil` fields. The registration payload has a top-level `registration` field. `issuer` is an encoded `W3C_VC_Identifier` object, not scalar text.

The procedure is intentionally optional and demonstrates supported indexing rather than satisfying a performance requirement. During the partial live run with 36 projected credentials, an `EXPLAIN` of exact lookup used a bitmap scan over the generated expression index. The fresh full 300+ live validation remained blocked as documented in the demo README. No GIN index is installed because the API has no containment query.

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
