# PQS DID interface queries

Only public PQS 3.5 functions are used, never physical `__*` tables or a transactions table. The BFT reader has no database access and remains singular-only.

Exact interface identifiers, shared with singular active queries:

- `canton-network-did-interfaces:Canton.Network.Did.V1:DidDocument`
- `canton-network-did-interfaces:Canton.Network.Did.V1:RegisteredDidDocument`

Fresh collection requests execute `select set_latest(NULL)` and retain its bigint result, then `select validate_offset_exists(?::bigint)`. Continuations validate their authenticated snapshot without selecting another latest offset. Every function invocation in a listing receives the same explicit offset.

| State | Public source |
| --- | --- |
| active | `active(?::text, ?::bigint)` |
| archived | `archives(?::text, 0::bigint, ?::bigint)` |
| all | `creates(?::text, 0::bigint, ?::bigint)` |

PQS bigint offset ranges are inclusive. A separate bounded archives source supplies archive metadata only through the snapshot, preventing later archives from leaking into earlier pages. Registered sources use the exact registered interface ID and join by contract ID, create event ID, creation offset and index; archived sources also match archive event ID and offset. The payload mapper rejects differing intrinsic documents across interface views.

Lifecycle columns are `template_fqn`, `payload_type`, `create_event_pk`, `create_event_id`, `created_at_ix`, `created_at_offset`, `created_effective_at`, `archive_event_pk`, `archive_event_id`, `archived_at_ix`, `archived_at_offset`, `archived_effective_at`. The API emits camelCase names beneath `lifecycle`, plus state. Null archive fields denote active-at-snapshot contracts, not registration deactivation. Effective-time filters use `createdFrom` and `archivedFrom` inclusive `>=` predicates and `createdUntil` and `archivedUntil` exclusive `<` predicates. Intervals with `From >= Until` are invalid. Either archive filter excludes active rows because their archive timestamps are null.

Ordering is `(payload -> 'id' ->> 'value', contract_id)`. Token-only continuation uses a strict tuple greater-than keyset; fresh pages or explicit token+page jumps use OFFSET. Fetch size is pageSize+1. `partyId` retains its existing exact `registration.registryAdmin` meaning and is only available for registered listings.

Singular queries retain active projections and LIMIT 2 duplicate detection. DSO bootstrap restricts candidates to `did:canton:DSO::` or an exact configured DID, without requiring a singleton intrinsic collection.

Runtime readiness executes latest selection, offset validation and all three registered historical query variants. Unsupported installed functions or columns fail readiness. Component tests lock exact SQL, bindings, bigint mapping and pagination without requiring Docker. Live PQS execution is separate deployment evidence.
