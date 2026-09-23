# Candidate Daml interfaces

This project contains the directly consultable candidate interface source for the CIP. It standardizes a base credential, an optional intrinsic lifecycle capability, registration metadata, holder-authorized removal and archival, initial issuance, and renewal. Concrete templates still define stakeholder visibility and any additional governance policy.

The generic `Credential` interface retains holder self-removal and all-holder archival. `Credential_RemoveSelfAsHolder` consumes the canonical contract and creates exactly one replacement without the exercising holder. It rejects non-holders and final-holder removal. The replacement preserves all other credential fields and, when registered, all registration fields. `Credential_ArchiveAsAllHolders` remains a terminal action jointly authorized by every holder and creates no replacement.

Natural expiry after `CredentialView.validUntil` is derived and requires no ledger transaction. The generic model defines no status or lineage fields and no generic reissue, suspend, resume, revoke, explicit-expiry, refresh, deregistration, retention-update, `ApplyUpdates`, or `Update` choices.

`CredentialLifecycle requires Credential` is an optional capability. Its consuming `CredentialLifecycle_Renew` choice is issuer-authorized and replaces the current credential with one whose `validUntil` is strictly later. An absent `validUntil` is unbounded and cannot be renewed to a finite time through this choice.

`RegisteredCredential requires Credential` only and is registration-only; registration does not make lifecycle support mandatory. `RegisteredCredentialLifecycle` owns registered renewal and requires `RegisteredCredential`, `CredentialLifecycle`, and `Credential`. The final `Credential` is conceptually implied by both parent interfaces but must be listed because Daml requires the full transitive requirement closure. Its consuming `RegisteredCredentialLifecycle_Renew` choice targets the exact current contract, is jointly authorized by the issuer and registry administrator, and accepts optional profile authorization and payment evidence. It atomically replaces the canonical contract, sets intrinsic `validUntil` and registration `expiresAt` to the same strictly later time, preserves all other credential and registration fields, and returns the replacement `ContractId RegisteredCredentialLifecycle`. The demo contract implements all four interfaces.

`CredentialRegistryFactory_Issue` is the only generic factory operation. It is controlled by the configured issuer, checks the credential issuer and registry administrator, creates one registered credential, and leaves the factory active.

The normative HTTP contract is [`openapi/credential-registry-v1.yaml`](openapi/credential-registry-v1.yaml), with canonical base path `/v1`. The intrinsic `/credentials` family reads `Credential` projections without requiring registration. The `/registered-credentials` family contains only contracts implementing both `Credential` and `RegisteredCredential`, joined by identical contract ID, and keeps `credential` and `registration` objects separate. This HTTP composition does not merge the Daml views or change `RegisteredCredential requires Credential`.

The contract is access-policy-neutral: deployments decide endpoint exposure, filtering, authentication, authorization, and audience. Active record retrieval is the baseline. Inactive retrieval and history require documented backing and separate event operations; unsupported requests must be rejected. The checked-in demo is active-only, exposes both record families at `/v1`, and rejects history and non-active lifecycle scopes.

`CredentialView.credentialTypes : [Text]` is the compile-valid Daml field because `type` is a reserved keyword. PQS exposes `credentialTypes`; W3C serializers map it to JSON-LD `type`. W3C-exportable representations must include `VerifiableCredential` among its values.

Every `CredentialSubject` has `id : Optional W3C_VC_Identifier`. `Some identifier` serializes as `credentialSubject.id`; `None` omits that property and may still carry claims. Validation and identifier mapping apply only when the ID is present. Implementations must not infer subjects, holders, controllers, or authorization from identifiers, claims, or arbitrary values. `CredentialView.issuer` uses the same identifier representation while retaining distinct issuer semantics.

The DSO profile keeps `CredentialView.validUntil` and `RegisteredCredentialView.expiresAt` conceptually distinct but requires equal values where both are present. DSO registered renewal extends both atomically to the same value. Payment is optional profile-specific authorization for `RegisteredCredentialLifecycle_Renew`; it is not required by the generic interface. A future payment profile must define authorization, payer, receiver, asset, pricing, payment-extension atomicity, replay and idempotency protection, failure and refund behavior, finality, concurrency control, privacy, metadata validation, and auditability.

The project uses Daml SDK `3.5.11` and targets LF `2.2`; the candidate source imports `AnyValue` from `splice-api-token-metadata-v1-1.0.0.dar`. The interface and model runtime packages do not depend on `daml-script`; only scripts/tests do. Build the Daml packages from `../demo` with:

```sh
make daml-build
make daml-test
```
