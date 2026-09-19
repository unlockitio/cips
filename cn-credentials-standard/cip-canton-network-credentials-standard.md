<pre>
  CIP: CIP TBD
  Layer: Daml
  Title: Canton Network Credentials Standard
  Author:
    Simon Meier
  License: CC0-1.0
  Status: Early Draft (comment threads at the bottom of this doc, and inlined TODO notes)
  Type: Standards Track
  Created: 2026-01-09
</pre>

# Canton Network Credentials Standard

## Abstract

Define a portable base credential contract standard, a credential registry interface and API standard built on that base, and a DSO-governed shared-registry deployment profile for storing, retrieving, discovering, and using registered credentials on the Canton Network.

## Specification

This CIP consists of two generic layers, presented in dependency order:

1. **Credential Contract and Registry Standards** defines two distinct surfaces:
   * the **Base Credential Contract Standard**, for intrinsic credentials that can exist and be used without a registry; and
   * the **Credential Registry Interface and API Standard**, a registry capability built on the base interface, with registry-specific metadata, lifecycle operations, read-only HTTP APIs, and visibility semantics. A conforming registry keeps its backend and APIs within the same responsibility boundary so consumers do not depend on its storage implementation.
2. **Application and Metadata Discovery** standardizes discoverable declarations for applications and services, including credential registries. Through namespaced properties on credential subjects, these declarations describe what is available, where its endpoints are, which capabilities or metadata it supports, and how clients interact with it. Layer 2 defines how a client that knows an identity, party, or application context can find compatible services and the information needed to invoke them. This CIP uses the Credential Registry Interface and API Standard as the current mechanism for publishing and resolving the declarations, while registries themselves can be discoverable services. The off-ledger Asset Registry API discovery requirements in CIP-56 inform this layer's reusable discovery mechanism.

The standard supports three deployment modes:

* **direct-issued Credential without a registry:** an issuer-specific workflow creates a contract implementing the base `Credential` interface without requiring a registry or registry HTTP API;
* **issuer-operated registry:** the issuer, or infrastructure it controls, adds the registry capability and APIs; and
* **third-party or shared registry:** a separate administrator registers credentials for issuers and holders under its own registry policy.

### Architecture Overview (Non-Normative)

The System Context view treats the Canton Network Credentials Standard as one system and shows only its people and external-system relationships. Internal interfaces and deployment components are intentionally deferred to the Container view. Credential issuance remains the responsibility of issuer-specific software. Private issuance applications support direct or restricted workflows without requiring registry publication; public issuance applications add registration for credentials intended to be publicly discoverable. Issuers identify the assessment policy under which they issue credentials. App Users use both app or wallet providers and network explorers as separate applications: wallets may support holder workflows, while explorers expose only publicly visible registered credentials and discovery metadata. Using a public explorer does not imply ledger Party authority. Issuers and holders may also use these applications. Entities may act in multiple roles. For example, a bank can issue credentials to its customers while also holding credentials issued to it by regulators.

![System context showing the Canton Network Credentials Standard as one system used through private and public credential issuance applications, an app or wallet provider, and a network explorer limited to public registrations and discovery metadata, with the DSO Party collectively controlled by all SVs administering and operating the DSO instance.](images/credentials-system-context.png)

*System Context for the Canton Network Credentials Standard and its external actors. [C4-PlantUML source](images/credentials-system-context.puml).*

The System Context is a simplification that separates public and private issuance capabilities for clarity; these labels describe roles, not required deployment boundaries. One issuance application or registry-facing deployment may implement either capability or both, provided public issuance exposes registry-visible material according to the applicable profile and private issuance preserves the applicable visibility, authorization, and privacy rules.

The System Context elements below identify the people and external systems shown in the diagram.

| Element | Type | Diagram role or boundary |
| --- | --- | --- |
| Credential Issuer | Person | Organization or individual acting in the issuer role. |
| Credential Holder | Person | Party acting in the holder role. |
| App User | Person | User of credential applications, wallets, and network explorers; using a public explorer does not imply ledger Party authority. |
| Canton Network Credentials Standard | System | The single in-scope system for portable credentials, registration, discovery, and the DSO deployment profile. |
| Private Credential Issuance Application | External system | Issuer-specific software for direct or restricted issuance and holder workflows. |
| Public Credential Issuance Application | External system | Issuer-specific software for registered credentials; the promoted DSO deployment profile is a concrete instance. |
| App / Wallet Provider | External system | Application or wallet that handles credentials for issuers, holders, and app users. |
| Network Explorer | External system | Explorer limited to publicly visible registered credentials and discovery metadata. |
| DSO Party / SV Governance | Person | The DSO Party, collectively controlled by all SVs, acting as administrator and operator of the DSO Credential Registry used by the promoted public issuance deployment. |

#### Credential Registry Reference Application (DSO-managed)

This CIP also promotes a DSO-managed Credential Registry reference application that applies both generic layers through one logical Credential Registry. The diagram separates public and private issuance capabilities for clarity. A single application may provide either capability or both. An issuance application uses the reference application but remains a distinct application component. The later DSO Credential Registry Deployment Profile defines its concrete deployment as one logical Credential Registry, administered and operated by a single DSO Party under the collective control of all SVs. The profile adds DSO-specific governance, registration expiry, fees, discovery, and endpoint publication. Implementations may expose the registry through multiple endpoints while preserving the same registry identity and governance model.

This is the initial DSO deployment profile. Future iterations may define additional deployment models, including delegated operation or partitioning.

### Layer 1: Credential Contract and Registry Standards

Layer 1 defines Daml interfaces for the base `Credential`, `RegisteredCredential`, and `CredentialRegistryFactory`, and HTTP interfaces for registry information, lookup, and bulk retrieval. Credential Lifecycle relates intrinsic validity, registration lifecycle, and archival or removal behavior. A base credential can exist without registration.

#### Daml Interfaces

The three candidate Daml interfaces are `Credential`, `RegisteredCredential`, and `CredentialRegistryFactory`. Their candidate definitions are checked in at [`interfaces/daml/Canton/Network/Credentials/V1.daml`](interfaces/daml/Canton/Network/Credentials/V1.daml), with candidate status and build instructions in [`interfaces/README.md`](interfaces/README.md); concrete templates, package naming for standardization, and signatories remain outside that source.

##### Credential Interface

A base credential represents issuer assertions about one or more credential subjects. The fields oriented toward the [W3C Verifiable Credentials Data Model 2.0](https://www.w3.org/TR/vc-data-model-2.0/) form the basis of `CredentialView`:

- credential identifier;
- credential types;
- issuer;
- credential subjects;
- validity interval.

Holders are separate Canton operational metadata.

Field position and explicit Daml authority keep identity and operational roles independent:

- issuer identity identifies the entity making assertions and does not imply holder status or Canton authorization;
- credential subjects identify who or what claims concern when an ID is present; a Party subject does not become a holder, controller, or authorized actor;
- Canton operational holders are explicitly listed Parties; holder status does not imply subject or issuer identity;
- controllers and authorization come from explicit interface choices and template authority, not generic payload inference.

In practice, clients use the typed fields according to their declared roles, and templates authorize operations explicitly. Implementations MUST NOT scan credential identifiers, claims, nested `AnyValue`, or other payload content to infer subjects, holders, controllers, or authorization.

The schema of base credentials is adapted from the [Draft PR](https://github.com/hyperledger-labs/splice/pull/3416/changes#diff-808147bf36f1c087a42b92d67d2021c2ca203076cc0e3b6a31f2ccc60497a34d). The core view contains the intrinsic credential fields and Canton holder metadata described below.

**Table 1. Core `CredentialView` fields**

  | CredentialView field | W3C VC Data Model 2.0 concept | External serialization | Canton semantics |
  | --- | --- | --- | --- |
  | `CredentialView.id : Optional Text` | Credential [`id`](https://www.w3.org/TR/vc-data-model-2.0/#identifiers) | Profile-defined URL when present | Semantic correspondence; the Daml value requires profile mapping. |
  | `CredentialView.credentialTypes : [Text]` | [`type`](https://www.w3.org/TR/vc-data-model-2.0/#types) | JSON-LD `type` values | Credential type values carried by the view. |
  | `CredentialView.issuer : W3C_VC_Identifier` | [`issuer`](https://www.w3.org/TR/vc-data-model-2.0/#issuer) | Identifier selected by the ADT variant | Issuer identity retains its field-specific role. |
  | `CredentialView.validFrom : Optional Time`; `CredentialView.validUntil : Optional Time` | [`validFrom`, `validUntil`](https://www.w3.org/TR/vc-data-model-2.0/#validity-period) | Profile-defined date-time representation | Start of validity and optional end of validity. |
  | `CredentialView.credentialSubject : NonEmpty CredentialSubject` | [`credentialSubject`](https://www.w3.org/TR/vc-data-model-2.0/#credential-subject) | One subject object or an array, as the profile permits | The interface enforces `1..n` subjects. |
  | `CredentialView.holders : NonEmpty Party` | Canton extension with no core-VC counterpart | Profile extension when defined | Canton operational metadata for the canonical contract. |

Semantic correspondence alone does not make `CredentialView` a conforming compacted JSON-LD VC. External conformance additionally requires the mandated [`@context`](https://www.w3.org/TR/vc-data-model-2.0/#contexts), URL [`identifiers`](https://www.w3.org/TR/vc-data-model-2.0/#identifiers) where the W3C model requires them, a [`securing mechanism`](https://www.w3.org/TR/vc-data-model-2.0/#securing-mechanisms), and a [`type`](https://www.w3.org/TR/vc-data-model-2.0/#types) including `VerifiableCredential`. See Credential Interface Normalization for detailed serialization and normalization rules.

`holders` is Canton operational metadata and MUST be excluded from standard W3C serialization unless a named extension profile defines its field, context, semantics, and security considerations. The interface view does not itself grant Daml visibility. Concrete templates define stakeholders and observers; when designated holders are made stakeholders, they see the full canonical credential and complete holder list.

The `credentialSubject` field expands into one or more subject records whose optional identifiers and claims have the following fields.

**Table 2. `CredentialSubject` fields**

  | CredentialSubject field | W3C VC Data Model 2.0 concept | External serialization | Profile semantics |
  | --- | --- | --- | --- |
  | `CredentialSubject.id : Optional W3C_VC_Identifier` | Optional credential subject [`id`](https://www.w3.org/TR/vc-data-model-2.0/#identifiers) | `Some identifier` emits `credentialSubject.id`; `None` omits it | Claims remain valid when the ID is absent. |
  | `CredentialSubject.claims : TextMap Api.Token.MetadataV1.AnyValue` | Properties of the [`credentialSubject`](https://www.w3.org/TR/vc-data-model-2.0/#credential-subject) | Direct properties of the same `credentialSubject` object | Namespaced property semantics and JSON-LD mapping are profile-defined. |

The issuer and each present subject ID use the same `W3C_VC_Identifier` sum type. Field position determines whether a value represents the issuer or a subject. The issuer maps by field position to W3C `issuer`; a present subject ID maps to `credentialSubject.id`. A Party value requires a profile-defined Party-to-URL-or-DID mapping. Text identifiers MUST be profile-permitted and validated; they may represent a DID, URI, or another approved identifier, and unvalidated text is invalid. Profiles SHOULD avoid putting raw national identifiers on-ledger or into public serialization unless necessary and authorized.

**Table 3. `W3C_VC_Identifier` variants**

  | Identifier type / constructor | Used by | Value carried | Serialization and validation |
  | --- | --- | --- | --- |
  | `W3C_VC_Identifier_Party Party` | `CredentialView.issuer`; present `CredentialSubject.id` | Canton-native `Party` | Requires a profile-defined Party-to-URL-or-DID mapping. Field position determines whether the external value maps to W3C `issuer` or `credentialSubject.id`. |
  | `W3C_VC_Identifier Text` | `CredentialView.issuer`; present `CredentialSubject.id` | Profile-permitted external identifier text | Requires profile validation before serialization. Field position determines whether the external value maps to W3C `issuer` or `credentialSubject.id`. |

Each entry in `CredentialSubject.claims` uses `Api.Token.MetadataV1.AnyValue`, including recursive collections and values that require profile-defined external identifiers. Claims are the subject-property carrier and flatten to direct properties without a `claims` wrapper. The claim key `id` remains reserved even when the subject ID is absent, so claims cannot manufacture or shadow `credentialSubject.id`.

**Table 4. `AnyValue` claim-value types**

  | Supporting type or constructor | Claim-value role | External representation | Serialization requirements |
  | --- | --- | --- | --- |
  | `Api.Token.MetadataV1.AnyValue` (`AV_Text`, `AV_Int`, `AV_Decimal`, `AV_Bool`, `AV_Date`, `AV_Time`, `AV_RelTime`, `AV_Party`, `AV_ContractId`, `AV_List`, `AV_Map`) | Subject-property value carrier | Profile's target representation | Canton-native typed values, not a complete JSON-LD value model. |
  | `AV_List`, `AV_Map` | Recursive collection values | Profile-defined array, set, or map representation | Nested values are mapped recursively. |
  | `AV_Party`, `AV_ContractId` | Values requiring profile-defined external identifiers | Profile-defined external identifiers | Native values require explicit interoperable mappings. |

The candidate Daml imports `AnyValue` from `splice-api-token-metadata-v1`. Profiles or future standardized interfaces may adopt that type or another stable compatible claim-value carrier. Interoperable Daml implementations coordinate the claim-value type and package identity used by the interface, and serializers and clients agree on external constructor semantics. Changing the carrier, its constructors, or package identity can require coordinated implementation, binding, serializer, and client updates; structurally similar Daml types are not automatically interchangeable.

```daml
import DA.NonEmpty (NonEmpty, toList)
import DA.TextMap (TextMap)
import Splice.Api.Token.MetadataV1 qualified as Api.Token.MetadataV1

data W3C_VC_Identifier
  = W3C_VC_Identifier_Party Party
  | W3C_VC_Identifier Text
  deriving (Eq, Ord, Show)

data CredentialSubject = CredentialSubject with
  id : Optional W3C_VC_Identifier
  claims : TextMap Api.Token.MetadataV1.AnyValue
  deriving (Eq, Show)

data CredentialView = CredentialView with
  id : Optional Text
  credentialTypes : [Text]
  issuer : W3C_VC_Identifier
  validFrom : Optional Time
  validUntil : Optional Time
  credentialSubject : NonEmpty CredentialSubject
  holders : NonEmpty Party
  deriving (Eq, Show)

data Credential_RemoveSelfAsHolderResult = Credential_RemoveSelfAsHolderResult with
  replacementCredential : ContractId Credential
  deriving (Eq, Show)

data Credential_ArchiveResult = Credential_ArchiveResult with
  archivedCredential : CredentialView
  meta : Api.Token.MetadataV1.Metadata
  deriving (Eq, Show)

interface Credential where
  viewtype CredentialView

  credential_removeSelfAsHolderImpl : ContractId Credential -> Credential_RemoveSelfAsHolder -> Update Credential_RemoveSelfAsHolderResult

  choice Credential_RemoveSelfAsHolder : Credential_RemoveSelfAsHolderResult
    with
      holder : Party
    controller holder
    do
      let currentHolders = toList (view this).holders
      assertMsg "exercising party is not a credential holder" (elem holder currentHolders)
      assertMsg "the final holder cannot relinquish the credential" (length currentHolders > 1)
      credential_removeSelfAsHolderImpl this self arg

  credential_archiveAsAllHoldersImpl : ContractId Credential -> Credential_ArchiveAsAllHolders -> Update Credential_ArchiveResult

  choice Credential_ArchiveAsAllHolders : Credential_ArchiveResult
    controller (toList (view this).holders)
    do
      credential_archiveAsAllHoldersImpl this self arg
```

`Credential_RemoveSelfAsHolder` is a consuming choice controlled exactly by its `holder` argument. The interface verifies that the controller is a current holder and that at least one other holder remains, then delegates concrete replacement construction to the abstract implementation method. The implementation MUST consume the old canonical credential, create exactly one replacement without that holder, preserve the remaining holder order, and return its `ContractId Credential` in `Credential_RemoveSelfAsHolderResult`. It MUST preserve logical credential identity, credential types, issuer, subjects and claims, intrinsic validity, and unrelated profile fields. A holder can remove only itself, never another holder.

The generic interface standardizes the choice, argument, controller, preconditions, consuming replacement semantics, and result. The implementing template or profile constructs the concrete replacement and MUST satisfy its signatory, observer, and authorization rules. A holder-controlled interface exercise does not authorize creation under arbitrary template signatories. This candidate therefore does not invent an issuer or signatory model, and includes no fabricated concrete fixture.

`Credential_ArchiveAsAllHolders` remains unchanged: it is a terminal action controlled jointly by every Party in `holders`; all holders must authorize it, and it creates no replacement. The base interface does not define issuer-authorized archival or revocation because `W3C_VC_Identifier Text` cannot control a Daml choice. A profile or template that needs either operation MUST define an explicit Party authorization mechanism and choice, and MUST NOT infer that Party from holders, subjects, claims, or external text. Wallets MAY remove their local reference or presentation without changing the ledger contract. They MUST distinguish that local action from holder relinquishment, global archival, issuer revocation, expiration, registry removal, and deregistration.

**Table 5. Base `Credential` choices**

| Choice | Controller | Preconditions | Consuming outcome |
| --- | --- | --- | --- |
| `Credential_RemoveSelfAsHolder` | Exactly `holder` | `holder` is current and at least one other holder remains | Replaces the canonical contract once, preserving credential state and remaining holder order; returns `replacementCredential : ContractId Credential`. |
| `Credential_ArchiveAsAllHolders` | Every current holder jointly | All holders authorize | Terminates the current canonical contract without replacement. |

###### Credential Interface Normalization

One logical VC uses one canonical `Credential` contract. Every credential MUST have at least one holder, and all current holders share its contract ID, full payload, and intrinsic validity. Concrete templates MUST reject duplicate holders and use deterministic Party ordering. The view is not mutated in place: holder self-removal consumes the canonical contract and creates one replacement through the standardized choice. The replacement MUST remove exactly the exercising holder and preserve the order of all remaining holders. Frequent independently mutable or holder-private relationships MAY require future separate holder-association contracts, but this CIP does not define them. Optional association contracts MUST NOT imply a relationship between a credential subject and a Party.

Concrete templates MUST reject the reserved key `id` in `claims`, including when the subject ID is `None`. Serializers emit a present subject identifier as `credentialSubject.id`, omit that property for `None`, and MUST flatten `CredentialSubject.claims` into direct properties without a `claims` wrapper. A subject with no ID may still carry claims; `None` does not guarantee anonymity. Multi-valued claims use `AV_List`. Serializers MUST recursively map `AV_List` and `AV_Map`, and MUST map every `AV_Party` and `AV_ContractId` to a profile-defined external identifier. Concrete templates MUST validate every present `W3C_VC_Identifier` according to the applicable profile and apply profile-defined claim constraints. Implementations MUST NOT infer a missing subject ID from claims, holders, the issuer, or nested values. Identifier lookup matches only present IDs; subjects with absent IDs do not match identifier filters.

This draft defines no canonical JSON-LD normalization. Deterministic ordering currently applies to holder Parties, not arbitrary claim keys or properties. Whether one `credentialSubject` is serialized as one object or an array remains profile-defined. For example, a claims-only subject can use `CredentialSubject { id = None, claims = TextMap.fromList [("profile.example/status", Api.Token.MetadataV1.AV_Text "active")] }`; omission of an ID makes no anonymity guarantee.

##### Registered Credential Interface

`RegisteredCredential` requires `Credential`, so a registry-enabled template exposes registration state in addition to its intrinsic view. Registration metadata is separate from `CredentialView`:

```daml
-- Candidate; sourced from interfaces/daml/Canton/Network/Credentials/V1.daml.
data RegistrationMetadata = RegistrationMetadata with
   registryAdmin : Party
     -- ^ The party administering this registration.
   registeredAt : Optional Time
     -- ^ The time at which the credential was registered.
   expiresAt : Optional Time
     -- ^ The time at which the registration expires under registry policy.
     -- This is independent of CredentialView.validUntil.
   meta : Api.Token.MetadataV1.Metadata
     -- ^ Registry-specific extensibility metadata.
 deriving (Eq, Show)

data RegisteredCredentialView = RegisteredCredentialView with
   registration : RegistrationMetadata
 deriving (Eq, Show)

interface RegisteredCredential requires Credential where
 viewtype RegisteredCredentialView

 registeredCredential_publicFetchImpl : ContractId RegisteredCredential -> RegisteredCredential_PublicFetch -> Update RegisteredCredentialView

 nonconsuming choice RegisteredCredential_PublicFetch : RegisteredCredentialView
   -- ^ Fetch registration metadata for a known and disclosed contract.
   -- This does not provide anonymous public discovery. Discovery and disclosure
   -- are supplied by the registry HTTP API and Canton transaction mechanisms.
   with
     expectedRegistryAdmin : Party
       -- ^ The expected party administering the registration.
     actor : Party
       -- ^ The party fetching the known contract.
   controller actor
   do
     result <- registeredCredential_publicFetchImpl this self arg
     assertMsg "unexpected registry administrator" (expectedRegistryAdmin == result.registration.registryAdmin)
     pure result
```

Clients project `CredentialView` and `RegisteredCredentialView` independently. `RegisteredCredential_PublicFetch` is a nonconsuming, actor-controlled operation for fetching registration metadata from a known and disclosed contract; it does not provide anonymous discovery. The expected party administering the registration is supplied as `expectedRegistryAdmin`. Implementations MUST validate that it matches the `registryAdmin` in the registration view. The check depends on callers obtaining that party from a trusted source; package vetting and registry-provider security review remain necessary, and apps and users that want to use registered credentials from a specific registry on-ledger must vet the registry's DARs.

When an implementation is also a `RegisteredCredential`, `Credential_RemoveSelfAsHolder` MUST preserve registration continuity. Embedded registration metadata MUST be copied unchanged. References keyed by contract ID MUST be updated atomically in the same transaction, or references based on stable logical identity MUST continue to identify the replacement correctly. Registration timestamps, expiry, fees, status, retention, and audit or history state MUST NOT reset. The generic result remains `ContractId Credential`; any stronger `RegisteredCredential` result or discovery mechanism is profile-specific. An implementation MUST NOT add a second registered self-removal choice that conflicts with or bypasses the base choice.

##### Credential Registry Factory Interface

A registry that implements this interface exposes the following bulk registration-update entry point:

```daml
data CredentialRegistryFactoryView = CredentialRegistryFactoryView with
  registryAdmin : Party
  deriving (Eq, Show)

data RegisteredCredentialUpdate = RegisteredCredentialUpdate with
  registeredCredential : ContractId RegisteredCredential
  registration : RegistrationMetadata
  deriving (Eq, Show)

data CredentialRegistryFactory_UpdateRegisteredCredentialsResult = CredentialRegistryFactory_UpdateRegisteredCredentialsResult with
  registeredCredentials : [ContractId RegisteredCredential]
  deriving (Eq, Show)

interface CredentialRegistryFactory where
  viewtype CredentialRegistryFactoryView

  credentialRegistryFactory_updateRegisteredCredentialsImpl : ContractId CredentialRegistryFactory -> CredentialRegistryFactory_UpdateRegisteredCredentials -> Update CredentialRegistryFactory_UpdateRegisteredCredentialsResult

  choice CredentialRegistryFactory_UpdateRegisteredCredentials : CredentialRegistryFactory_UpdateRegisteredCredentialsResult
    with
      actor : Party
      updates : [RegisteredCredentialUpdate]
    controller actor
    do
      credentialRegistryFactory_updateRegisteredCredentialsImpl this self arg
```

The actor-controlled choice carries known `RegisteredCredential` contract IDs and replacement `RegistrationMetadata`; it does not mutate the intrinsic `CredentialView` or attach an interface dynamically to an existing contract. Concrete factory implementations define and enforce actor and registry-administrator authorization, verify that each target belongs to the registry, and preserve intrinsic issuer, canonical `holders`, and `NonEmpty CredentialSubject`. The interface does not define the issuer's assertion policy. Initial creation and registry-removal workflows remain unresolved and are not fabricated by the checked-in candidate source.

Conforming registries SHOULD implement the Credential Registry Factory Interface to enable portable third-party registration. A registry MAY limit registration to internal workflows only when its profile explicitly declares that limitation through Registry Info capabilities. The current Registry Info API text does not yet define a capabilities field, so the exact declaration mechanism is an open design item. Supporting the factory improves issuer portability and interoperability; restricting registration can simplify authorization, policy enforcement, and operations at the cost of portability.

The candidate interface is a semantically breaking replacement for the draft `CredentialFactory` / `CredentialFactory_UpdateCredentials` surface. Prototype and reference implementations MAY temporarily retain old symbols as compatibility adapters, but those symbols are outside this candidate interface and this CIP does not claim that such compatibility code exists. A corresponding HTTP API endpoint allows retrieval of the context needed by the selected factory version. Earlier drafts are available in [Splice/Api/Credential/RegistryV1.daml](https://github.com/hyperledger-labs/splice/pull/3416/changes#diff-808147bf36f1c087a42b92d67d2021c2ca203076cc0e3b6a31f2ccc60497a34d) and [openapi/credential-registry-v1.yaml](https://github.com/hyperledger-labs/splice/pull/3416/changes#diff-a73145dfdb26770f01b5fc0a9f35c7c34f067584acb6ec16de7e82040df6f835).

#### HTTP Interfaces

The HTTP interfaces apply only to registry-enabled credentials; implementing `Credential` alone does not expose them. They provide registry information, exact lookup, and bounded bulk retrieval. Registry publication classifies records as **public**, **restricted**, or **private** according to registry policy and issuer input. Public records may be returned to unauthenticated API clients and indexed by explorers. Restricted records require an authorization policy. Private records and their contents are not exposed through public APIs. These classifications do not change Daml contract visibility or stakeholder obligations.

> **Review note:** The profile must define which party makes the final visibility-classification decision when issuer input and registry policy differ.

##### PQS-backed Read Model and Request Flows

The Canton ledger is the source of transaction truth. Participant Query Store (PQS) continuously indexes contracts visible to its configured ledger identity and exposes a PostgreSQL reader API. Registry APIs perform read-only queries against the documented PQS SQL surface, including `active(...)` for filtered projection scans and joins, and MUST NOT depend on PQS physical `__*` tables. A deployment MAY use `lookup_contract(...)` when a contract ID is already known; the demo uses `active(...)` for both exact logical-ID lookup and pagination. The registry API does not run a custom ledger-ingestion or event-indexing pipeline.

A registry-enabled credential is represented in PQS by active `Credential` and `RegisteredCredential` interface projections for the same contract ID. The `Credential` projection supplies intrinsic credential fields, while the `RegisteredCredential` projection supplies registration metadata. Registry API results MUST associate these projections by contract ID and MUST use only rows visible to the configured PQS ledger identity. API reads are eventually consistent with PQS and may lag the Canton ledger.

![Sequence diagram showing PQS-owned indexing, single lookup, and bounded bulk pagination.](images/credentials-http-api-sequence.png)

*PQS-backed HTTP read sequence. [PlantUML source](images/credentials-http-api-sequence.puml).*

Implementations MAY create narrow payload indexes through indexing facilities supported by the deployed PQS version. Such indexes are optional query optimizations and do not introduce a separate ingestion path. PostgreSQL table layout, JSON representation, index expressions, helper signatures, and index maintenance are deployment concerns and are not normative in this CIP.

##### Credential Registry Info API

The credential registry info API enables asynchronous rollout of newer API versions and informs clients about registry constraints, such as limits on subject claims or result counts. Credential registries MAY enforce limits on stored credentials. Registry Info SHOULD expose supported filters and default and maximum page sizes once the external OpenAPI schema defines their representation. See [DSO Credential Registry Limits](#dso-credential-registry-limits).

A capability declaration for registries that restrict registration to internal workflows remains open. A draft API is specified in [openapi/credential-registry-v1.yaml](https://github.com/hyperledger-labs/splice/pull/3416/changes#diff-a73145dfdb26770f01b5fc0a9f35c7c34f067584acb6ec16de7e82040df6f835); alignment is an external follow-up.

##### Credential Lookup API

The lookup API queries active `Credential` and `RegisteredCredential` interface projections in PQS and associates them by contract ID. Intrinsic credential fields come from `Credential`, while registration metadata comes from `RegisteredCredential`. An exact logical credential-ID lookup reads the logical ID from the `Credential` projection and returns the associated registration data when the corresponding active `RegisteredCredential` projection is present. It returns one record, not found, or a duplicate-key conflict according to the external API contract. Other supported filters may return collections. Optional payload indexes MAY be used only through facilities supported by the deployed PQS version; they do not change the projection model.

Results expose only credential and registration fields allowed by registry policy. "Public" describes HTTP retrieval policy, not automatic Canton visibility. PQS-observed activeness can lag the ledger. When a profile supports authorized disclosure, a Daml transaction may use `RegisteredCredential_PublicFetch` for transaction-time confirmation; an indexed row or disclosure blob alone does not prove current activeness, registration validity, or intrinsic credential validity.

##### Bulk Credential Retrieval API

Bulk retrieval uses ordinary bounded SQL pagination over active PQS projections associated by contract ID. Requests use zero-based `page` and positive `pageSize`: `page` identifies the page number, `pageSize` is the maximum number of returned items, and the starting offset is `page * pageSize`. Deployments MUST define a default page size and maximum, reject invalid or excessive values, use deterministic ordering with a contract-ID tie-breaker, and fetch at most `pageSize + 1` associated rows with `LIMIT` and `OFFSET` to determine whether another page exists. A response reports `items`, `page`, `pageSize`, and `hasNext`; a total count is not required.

Offset pagination does not provide snapshot consistency. Concurrent PQS updates can shift page boundaries and cause clients to observe duplicates or omissions across requests. This API does not provide snapshot or ingestion-grade guarantees.

#### Credential Lifecycle

`CredentialView.validFrom` and `CredentialView.validUntil` define intrinsic usability and expiry. `RegistrationMetadata.registeredAt` and `RegistrationMetadata.expiresAt` define registry retention for the one canonical logical credential; registration attaches once, not separately to each holder. Holder relinquishment replaces the canonical contract without terminating the logical credential or resetting intrinsic validity or registration. Registry expiry, extension, removal, or deregistration does not renew, replace, or extend intrinsic validity.

![Two-lane credential lifecycle: a holder may relinquish only itself through consuming canonical-contract replacement while preserving credential and registration state; jointly authorized all-holder archival terminates the canonical contract without replacement; intrinsic validity and registration retention remain distinct.](images/credentials-lifecycle.png)

*Candidate lifecycle view showing holder relinquishment by replacement, terminal all-holder archival, intrinsic validity, and the independent registry-retention path. [PlantUML source](images/credentials-lifecycle.puml).*

Local wallet removal, holder relinquishment, Daml archival, intrinsic expiry, revocation or suspension, registration expiry, and deregistration are distinct actions or states. Relinquishment removes only the exercising holder and replaces the contract; all-holder archival terminates the current canonical credential without replacement. A removed holder loses future stakeholder visibility according to the concrete template's stakeholder behavior, but ledger history and data already observed cannot be erased. A registry MAY remove or archive a registration after registration expiry. A registry MAY define an operation that extends retention before or after registration expiry. Whether an extension updates the registration, creates a replacement registration, or requires a new registration remains registry policy and is not standardized here.

TODO: confirm whether clients should create a new credential approximately 24 hours before intrinsic credential expiry to avoid contention with prepared transactions referencing the old credential. This unresolved, non-normative client strategy would replace the intrinsic credential; it would not be registration extension or renewal. The client-side last-write-wins semantics for name resolution supports this approach.

<a id="application-and-metadata-discovery"></a>
### Layer 2: Standardized Application and Metadata Discovery

Layer 2 standardizes the current candidate mechanism for discovering applications and services, including credential registries: namespaced properties directly on a `CredentialSubject`, published and resolved through the Credential Registry Interface and APIs. A property value MAY carry endpoint, capability, or invocation information when its namespaced property definition assigns that meaning; multi-valued declarations use `AV_List`. Following the concrete Asset Registry API discovery need and form informed by CIP-56, a declaration associates a known subject, registry administrator, or application context with that service information. A client that knows that context can query the Credential Registry mechanism to resolve compatible service locations and the information needed to use them. App providers decide which discovered services and credentials influence their UIs and on-ledger workflows. CIP-56 remains the informing reference for Asset Registry API discovery; it is not a DID mechanism or a bootstrap authority for the Credential Registry, and this CIP does not change it.

This model is conceptually similar to [W3C DID resolution](https://www.w3.org/TR/did-resolution/) and DID Document [`service` entries](https://www.w3.org/TR/did-core/#services): DID resolution resolves a known DID to a DID Document, whose `service` entries can advertise service metadata and endpoints. DID Core does not by itself establish endpoint trust, availability, API correctness, or BFT read semantics.

DID-based service discovery is outside this CIP and is neither a dependency nor a conformance requirement. A future Canton Network DID standard or CIP could define it as an alternative or evolution by mapping this layer's service types and declarations to DID Document `service` entries and specifying precedence, coexistence, and migration. Until such a CIP is defined, namespaced subject properties resolved through the Credential Registry remain this CIP's normative current candidate mechanism.

A concrete deployment must define how a client obtains an initial discovery context or entry point. This bootstrap concern is deployment-specific: Layer 2 does not provide zero-config discovery or define a universal bootstrap authority, and it does not require that the client already know the registry it ultimately discovers or uses. The DSO profile defines one concrete discovery mechanism below. Direct issuance can operate without this mechanism under the workflow conditions described above, while clients can use discovery separately for related applications and services.

This CIP defines the following namespaced service-declaration keys:

- `cip-TBD/credential-registry-urls`: declares the availability and locations of the off-ledger API for a specific registry administrator party `admin`. The administrator publishes its party, API version, supported capabilities, and a list of URLs. Multiple URLs MAY identify access endpoints for the same logical registry; endpoint consistency and read guarantees are deployment-specific unless an applicable profile defines them.

- `cip-TBD/credential-issuer-app-url`: declares the availability and location of the dApp for a specific credential issuer party `issuer`. It is self-published by `issuer` according to the registry's publication rules and may be accompanied by namespaced properties describing supported interactions, capabilities, or invocation parameters. A wallet can read a user's credentials from the user's node, use the deployment's discovery entry point and Credential Registry mechanism to resolve this property, and offer a redirect to the issuer-specific dApp. The redirect may specify a `credential-contract-id=<contract-id>` query parameter. Providing this UI remains optional for credential issuers.

In general, subject property names use the form `namespace/property`, where the namespace is either:

1. `cip-<nr>` for properties defined in a CIP; or
2. `<dns-name>` for properties defined by an organization that controls that DNS name.

Layer 2 generalizes this discovery need so individual CIPs can define namespaced declarations for their own service families. Its subject-property mechanism is informed by CIP-56's concrete need and form for off-ledger Asset Registry API discovery, while this CIP defines publication and resolution through Credential Registry APIs.

#### Service and Application Discovery

The application and service discovery problem on the Canton Network arises when a client knows an application, provider, party, or contract context but does not yet know which compatible service is available, where its endpoint is, which capabilities or metadata it supports, or how to invoke it. Layer 2 standardizes namespaced declarations for this information, including declarations for credential registry services.

A typical example is a wallet that knows the registry `admin` party-id on a `Holding` contract owned by its user but does not yet know the compatible off-ledger registry service, its available URLs, or how to use its API. The wallet resolves this information to read token metadata such as total supply and to obtain the data required to transfer the `Holding`.

As described in [Application and Metadata Discovery](#application-and-metadata-discovery), publishing these declarations as well-known namespaced subject properties lets a client resolve the service and its interaction metadata through this CIP's Credential Registry mechanism. The deployment supplies an initial discovery context or entry point; the [DSO Registry Discovery](#dso-registry-discovery) profile defines discovery for its one logical registry.

##### Application Discovery

The list of all featured applications can be retrieved by querying for the
corresponding credential issued by the `dso` party with property
`cip-TBD/is-featured-app`.
Fetching the public credentials held by their application provider party then allows discovering further meta information about the application.

#### Profile Publication

The problem of profile publication is how to enable the useful functionality of party owners self-publishing well-known metadata (e.g., website, LinkedIn profile, dApp URL) about themselves. This is common functionality in many systems and helps connect the system’s users.

This information is typically unverified, which is fine as long as that information is not used to resolve names, but only to present additional details on a party (e.g., shown on hover).

Such self-published profile information can be published in a credential registry using credentials where the issuer is a member of `holders` and with an appropriate namespaced subject property. For example, the owner of a party `p` could publish their website using a subject object of the form

```text
credentialSubject = [
  CredentialSubject {
    id = Some (W3C_VC_Identifier_Party p),
    claims = TextMap.fromList [
      ("profile.example/website", Api.Token.MetadataV1.AV_Text "<url>")
    ]
  }
]
```

Here the map entry is a W3C-style property-value relation about the identified credential subject. External serialization maps the native Party subject to an application-profile URI or DID; it does not serialize the Daml `Party` value directly. The profile may independently include `p` in `holders`, but the Party subject does not make `p` a holder or lifecycle controller automatically.

To ensure that different applications interpret profile information the same way, a future CIP should standardize the common properties used in profiles (e.g., by building on the corresponding ENS standard [ENSIP-18](https://docs.ens.domains/ensip/18/)).

Applications may also need to discover registries storing a user’s profile information. A deployment profile can provide an entry point for this purpose; the DSO profile provides discovery for its one logical registry, while generic Layer 2 does not require that profile or a preexisting DSO registry.

#### Party Name Resolution

Party-ids are globally unique identifiers used in the Canton Network. However, they are difficult to use for humans as they are neither memorable nor easily comparable. Below we sketch how to build a name resolution system on top of the Credential Registry Interface and API Standard. We do so in two steps:

1. We explain how to resolve names managed by a single issuer within a single credential registry.
2. We explain how to resolve names across multiple issuers and credential registries.

##### Single Issuer and Single Credential Registry Name Resolution

Functionally this is what CNS 1.0 provides, where the `dso` party is both the issuer and credential registry administrator. A CNS record for user `p` with name `n` corresponds to a credential with:

```text
credentialSubject = [
  CredentialSubject {
    id = Some (W3C_VC_Identifier "<validated-cns-name-identifier>"),
    claims = TextMap.fromList [
      ("cip-TBD/cns-owner", Api.Token.MetadataV1.AV_Party <p>)
    ]
  }
]
```

The credential is issued by the `dso` party. The CNS profile validates the external CNS identifier and independently authorizes `p` as a member of `holders`; the `Api.Token.MetadataV1.AV_Party` property value alone does not grant that role. We can read this credential as “The `dso` party claims that the CNS name \<n\> is owned by \<p\>”. The CNS name is the identified subject, while holder authorization is separate Canton operational metadata. An external VC profile must map that `Party` value to an identifier/URI/DID. With multiple subject objects, `credentialSubjectId=<n>` matches a credential when at least one subject has an external serialization of that identifier; `keyPrefix` is evaluated against property names of matching subjects.

Resolving a CNS name `n` to the party holding it can be done using:

```text
GET /credential-registry/v1/credentials?issuer=<dso>&credentialSubjectId=<n>&keyPrefix=cip-TBD/cns-owner
```

The response will contain the credential listing the owner of the CNS name if it exists. The guarantee that there is at most one such credential is provided by the `dso` party as the issuer, which shows why it is paramount to constrain the query with `issuer=<dso>`.

The reverse lookup to query all names of a party `p` can be done using:

```text
GET /credential-registry/v1/credentials?holder=<p>&issuer=<dso>&keyPrefix=cip-TBD/cns-owner
```

The singular `holder=<p>` query parameter filters by membership: the response will list one credential per CNS name for which `p` is a member of `CredentialView.holders`.

The [draft PR shows here](https://github.com/hyperledger-labs/splice/pull/3416/changes#diff-6ffb0d08eee67175e91eabd4e3bf1d811e8ab18a029a541d169aa482fe3e294a) the earlier prototype approach of implementing `Credential` directly on the existing `AnsEntry` template to expose CNS 1.0 entries through the Credential Registry API. Under the candidate split in this draft, a registry-exposed template exposes `Credential` and `RegisteredCredential`; the exact migration of `AnsEntry` requires the Daml SDK compile spike. The issuance of CNS 1.0 entries continues to use the existing workflow. This matches the overall design of this CIP, which gives issuers full freedom in their issuance workflows.

##### Multi-Issuer and Multi-Registry Name Resolution

The basic idea is to follow the DNS construction and use hierarchical names and recursive resolution. Resolution is always done against a specific pair of an `issuer` and a registry `admin`. The root of the resolution is CNS with issuer `dso` and registry administrator `dso`.

Managing the issuers associated with root entries (i.e., top-level domain registrars) requires defining suitable off-ledger governance. The association itself can be stored on-ledger as a credential issued by the `dso` party. We expect that such governance can be built by adopting existing policies like the ones from ICANN for the Canton Foundation.

To support deduplicating names across multiple organizations issuing them concurrently, we expect that a more specific name registry interface will need to be developed. It can be implemented without contract keys by having the name registry administrator maintain the key-value map on-ledger in a scalable fashion (e.g., as a radix tree).

The main challenge we see with multi-issuer, multi-registry name resolution is actually not in the name issuance and resolution aspect, but the design problem of how to cleanly allow apps to leverage the existing names that entities have in off-ledger systems like DNS, email, ENS or LEI.

#### Verified Identities

There are many systems that associate names with entities. In particular, DNS names and email addresses are well-known and widely used to identify counter-parties. In contrast to CNS 1.0, these systems have their authoritative data source off-ledger. Thus we cannot expect that statements about party-to-name associations in these systems can be resolved directly on-ledger.

However, we do want to support imports of statements in the form "I \<issuer> have verified that party \<p> controls name \<n> in system \<S>". We can represent this using credentials issued by `issuer`, with `p` independently included in `holders` only when the profile authorizes that operational role, and with:

```text
credentialSubject = [
  CredentialSubject {
    id = Some (W3C_VC_Identifier "<validated-name-identifier>"),
    claims = TextMap.fromList [
      ("identity.example/<S>-controller", Api.Token.MetadataV1.AV_Party <p>)
    ]
  }
]
```

This models the verified external name as the subject and the controlling Canton party as a typed property value. The applicable profile validates the External identifier and independently decides whether that Party is a holder; neither holder membership nor Canton choice authority follows from the identifier or property value. An External DID does not itself control Canton choices. An external VC profile must define how both the subject identifier and `Party` value map to interoperable identifiers such as URIs or DIDs.

Once represented that in this way these can be used for name resolution in the same way as explained for CNS 1.0 above.

Issuers have full control over the verification they do before issuing such a credential. For example, doing DNS verification using a [DNS challenge](https://letsencrypt.org/docs/challenge-types/#dns-01-challenge). Information about the verification can be represented using defined VC properties, credential types and contexts supplied by a profile, extension-profile fields, or namespaced subject properties as appropriate. There is no generic claim-metadata container, and care should be taken to avoid bloating the credential.

App providers can choose which issuers to query for names in what systems to resolve party names in their applications. They can combine multiple naming sources by issuing multiple name resolution queries.

##### Consistent Cross-App Identities

For identities to work consistently across multiple applications it is important that these apps use compatible name resolution strategies, including compatible lists of issuers and registries. We envision that this can be built for Canton Network in a future CIP that standardizes two aspects:

1. How names from different systems are represented in a single namespace as ASCII strings.
2. How to build suitable Canton Foundation governance such that most apps can use the same issuer and registry configuration.

#### KYC Verification Services

KYC verification imports issuer statements about the physical world using processes that vary between organizations. The issuer is responsible for defining and applying the assessment policy under which it issues a KYC credential. A verification status may be restricted to authorized consumers. Identity details, evidence, and supporting documents are private and outside the public DSO registry.

> **Review note:** A future KYC profile must define the exact boundary between a shareable verification status and private evidence before it standardizes KYC publication.

These statements are relative to the issuer's process. Publication does not establish regulatory quality or legal validity. App providers can consume external KYC services without network-wide standardization. Custom properties should use the DNS name of their defining organization as a prefix; for example, `acme.com/custom-property`.

Organizations can use experience with restricted registries to inform a future CIP without making private data handling part of this iteration.

### DSO Credential Registry Deployment Profile

The current DSO profile has one logical DSO Credential Registry. One DSO Party, collectively controlled by all SVs, is both the registry administrator and operator. Separating the administrator and operator concepts does not create separate parties or delegated operators in this profile: both roles are assigned to that same DSO Party. Registry administration does not make the DSO Party an issuer, holder, or other stakeholder of every credential.

The current profile does not partition registry state or workload by issuer namespace, assign operation to Validators, delegate operation to other parties, or use Scan as a registry router or proxy. How individual SV infrastructure instances divide physical operational work is outside this specification. Internal distribution, replication, or multiple access endpoints, if implemented, MUST present one logical registry and MUST NOT imply separate registries, public partitions, or namespace-based routing.

#### Deployment Architecture, Administration, and Operation

![Container view showing one logical DSO Credential Registry, with the DSO Party collectively controlled by all SVs serving as both administrator and operator, and clients and explorers using its registry and discovery APIs directly.](images/credentials-containers.png)

*Container view of the DSO Public Credential Issuance Application instance and its one logical DSO Credential Registry. Physical hosting topology is intentionally unspecified. [C4-PlantUML source](images/credentials-containers.puml).*

The DSO profile applies the generic registry and discovery surfaces defined by Layers 1 and 2. Registry Info advertises the DSO Registry's constraints, API version, supported capabilities when defined, and endpoint information. Advertising registration capabilities, including an internal-workflow-only limitation, awaits the open generic capability-declaration design. Clients and explorers use the published endpoint information without depending on the registry's physical storage or hosting topology. The following subsections define the DSO profile's concrete expiry, access, discovery, and operating model.

#### Concrete Expiry and Paid Extension Policy

By default, there is no CC payment required for registering credential records in the DSO Credential Registry. Instead, the registry expires registrations within 90 days, configurable by SV voting, so that traffic from registration and registration-retention extension contributes toward storage cost. This is registration expiry and does not set or shorten the top-level `CredentialView.validUntil` value.

##### Extended Expiration Durations

The registry supports a paid registration-extension operation that can extend the registration expiration duration beyond 90 days by burning a CC fee configurable by SV voting. The fee purchases registry retention only; it does not extend credential validity.
This burn is executed by performing a CC transfer to the `cip-112/burn` account defined in
[CIP-112](https://github.com/canton-foundation/cips/blob/main/cip-0112/cip-0112.md#4321-special-account-identifiers-for-mint-and-burn) (Token Standard V2) with the following two extra arguments of `V2.TransferFactory_Transfer`:

- `cip-TBD/extend-credential-expiry-to` set to the new expiration time in `extraArgs.meta`
- `cip-TBD/credential-contract-id` set to the contract-id of the credential in `extraArgs.context`

The payment requires authorization from the sender of the funds and from at least one of the credential issuer or a party in `holders`.

This authorization policy allows credential issuance apps to extend registration expiration on behalf of the issuer or one of the credential's holders, as part of an app's workflow.
For example by creating the credential and extending its expiration in the same
transaction.


#### Deployment Components and Access

The APIs are implemented as follows:

1. The `splice-amulet-name-service` package is extended with two templates as prototyped in [this PR](https://github.com/hyperledger-labs/splice/pull/3416/changes#diff-271a41476c5ed80c77cbe363f39cc58f5f422a6c9991cfc2fa2bd65398802d7e), subject to the breaking draft migration described below.
   1. The `AnsCredentialFactory` template implements the candidate `CredentialRegistryFactory` interface. A prototype retaining `CredentialFactory` requires an explicit compatibility adapter or migration.
   2. The proposed `AnsCredentialRecord` template exposes both `Credential` and `RegisteredCredential` on one canonical credential contract.
2. The registry backend and standard APIs operate for the DSO Party, which is both administrator and operator of the one logical registry.
3. DSO-governed discovery records announce the DSO Party, API version, supported capabilities when defined, and registry API URLs.

Clients query the DSO Credential Registry APIs directly. Explorers retrieve deliberately public records through the Bulk Credential Retrieval API. The profile does not assign registry operation to Validators, require a Scan proxy, or require a custom explorer or registry ingestion pipeline. If multiple access endpoints are published, they provide access to the same logical registry; their physical hosting, availability design, and consistency guarantees are outside this iteration.

Scalability is measured for the logical registry. Representative load tests should measure PQS projection growth, API throughput and latency, storage, and operating cost before more complex mechanisms are considered.

#### Economic, Business, and Operational Considerations (Informative)

The DSO profile's registration expiry, paid registration extension, CC burn, and governance parameters provide a transparent mechanism for discussing recurring registry-retention activity without claiming a forecast. Let:

* `F` be the default fee in CC for one paid extension event, as configured by governance;
* `P` be the number of parties eligible to publish records;
* `r` be the average number of eligible records per party;
* `z` be the fraction of eligible records adopted into the registry;
* `e` be the average number of paid extension events per adopted record per period;
* `Y = P * r * z * e` be paid extension events per period; and
* `B = F * Y` be CC burned per period.

This is parametric arithmetic, not a prediction. `P` and `r` are counts, `z` is a dimensionless fraction between zero and one, `e` is events per record per stated period, `Y` is events per that period, and `B` is CC per that period. The draft defines no numeric value for these variables. Any use of the model MUST identify the network, period, eligible population, governance-configured `F`, and assumptions behind `r`, `z`, and `e`. Free renewals, policy exemptions, or records that expire without paid extension are outside `Y`.

Token Standard V1 wallets can be used to burn CC to extend the registration-retention duration of registered credentials by encoding the special receipt account and the extension parameters as follows:

- set `transfer.receiver` to the special `cip-112_no-owner::1220000000000000000000000000000000000000000000000000000000000000abcd` party
- set `cip-112/receiver.id` to `cip-112/burn` in `transfer.meta`
- set the other two arguments `cip-TBD/extend-credential-expiry-to` and `cip-TBD/credential-contract-id` as explained above

<a id="dso-registry-discovery"></a>
#### DSO Registry Discovery

This section defines discovery for the DSO profile's one logical registry; it does not make a preexisting registry a requirement of generic Layer 2. The DSO Party publishes the generic `cip-TBD/credential-registry-urls` declaration with its party, API version, supported capabilities, and one or more access URLs. All published URLs identify the same logical DSO Credential Registry. This iteration does not define a namespace-specific route property, partition assignment, or routing history.

The DSO Party can also publish profile-specific properties such as `cip-TBD/is-featured-app` for an application provider subject:

```text
credentialSubject = [
  CredentialSubject {
    id = Some (W3C_VC_Identifier "<validated-application-provider-identifier>"),
    claims = TextMap.fromList [
      ("cip-TBD/is-featured-app", Api.Token.MetadataV1.AV_Bool True)
    ]
  }
]
```

Each example uses one External subject, but the same credential may contain additional subject objects. The application provider may independently be included in `holders`. Clients MUST NOT infer holder membership, Party identity, or lifecycle control from the subject identifier or claims.

Clients obtain the DSO Registry discovery declaration from the deployment's published entry point and call one of the advertised Registry API URLs. The exact bootstrap channel is outside this iteration. Publishing multiple URLs does not imply separate registries, partitioned state, or namespace-based route selection.


<a id="dso-credential-registry-limits"></a>
#### DSO Credential Registry Limits

TODO: inline the explanations from the source code

- for now see the [source code here](https://github.com/hyperledger-labs/splice/pull/3416/changes#diff-271a41476c5ed80c77cbe363f39cc58f5f422a6c9991cfc2fa2bd65398802d7eR105)

## Motivation

Canton Network applications increasingly need to publish and discover public credentials for service discovery, party profiles, name resolution, and identity or KYC integrations. The off-ledger Asset Registry API discovery requirements in CIP-56 provide a related precedent for reusable service discovery, with CIP-56 retaining its own Asset Registry endpoint semantics. A common public credential registry and read APIs allow applications to discover credential-related information consistently and provide reusable building blocks for credentials issued under application-specific policies.

This CIP defines a portable base credential, registry APIs for registry-enabled credentials, and a public DSO shared-registry deployment profile. In this increment, one DSO Party, collectively controlled by all SVs, administers and operates one logical DSO Credential Registry while credential issuance and interpretation remain with issuers and consuming applications. Registry administration does not make the DSO Party the universal issuer, holder, custodian, or other stakeholder of all public credentials.

## Rationale

<a id="use-case-analysis"></a>
### Use-Case and Design Requirements

The base credential, registry capability and APIs, and DSO profile are building blocks for the visibility-classified examples above. The following sketches remain informative: they demonstrate the building blocks but do not standardize issuer verification, legal reliance, or application-specific authorization. Future CIPs may standardize common subject properties and resolution mechanisms.

#### Visibility Use Cases and Boundaries (Informative)

Short examples are:
* **Public:** token metadata; public service discovery, profiles, and name resolution; and deliberate publication of bond term-sheet metadata. Bond publication should normally contain a content hash, version, effective date, status, and URI. It does not imply that investor allocations or private terms are stored on-ledger.
* **Restricted:** KYC verification status when its issuer and holder do not intend unrestricted publication.
* **Private:** KYC evidence, personal details, and supporting documents, which are outside the public DSO registry.

Publication records who asserted data and makes the disclosed content inspectable. It does not by itself prove truth, regulatory quality, legal validity, or suitability for a consuming application's purpose.


The design therefore separates publication and retrieval capability from the policy and legal decisions made by registry administrators, issuers, holders, and consuming applications. Public, restricted, and private modes are protocol capabilities; concrete DSO publication policy belongs to the deployment profile.

#### Decisions, Alternatives, and Deferred Questions

The current design uses `NonEmpty CredentialSubject`, with each subject carrying `id : Optional W3C_VC_Identifier` and `claims : TextMap Api.Token.MetadataV1.AnyValue`; `CredentialView.issuer` uses the same identifier representation non-optionally with distinct issuer semantics. The identifier ADT distinguishes Canton-native `Party` identifiers from profile-permitted and validated external identifiers, which are not automatically DIDs. A present subject ID serializes as `credentialSubject.id`; an absent ID omits that property while the subject may still carry claims. Each claim entry flattens to a direct property on the external W3C subject object; no `claims` wrapper is serialized. Multi-valued claims use `AV_List`, and `id` remains a separate field and a reserved claim key. The wrapper and triple alternatives are closed for this draft. `AnyValue` provides Canton-native typed values, including recursive lists and maps, but the exact mapping of arbitrary nested values into JSON-LD remains unresolved. Layer 2 likewise selects namespaced subject properties resolved through Credential Registry APIs for the current candidate; DID-based discovery remains a future path. External serialization and context rules, the exact Party-to-URI-or-DID mapping, treatment of the Canton holder extension, status/schema/evidence extension profiles, OpenAPI alignment for pagination, and liability for cross-organization KYC reliance remain unresolved.

## Scope and Non-Goals

This CIP defines the Base Credential Contract Standard, the Credential Registry Interface and API Standard, Application and Metadata Discovery, and a public DSO Credential Registry Deployment Profile. The first increment includes a registry-independent credential surface plus a registry capability, backend, and APIs that apply only to registry-enabled credentials, namespaced discovery declarations, and one logical DSO Credential Registry administered and operated by the DSO Party collectively controlled by all SVs. The DSO profile does not currently define partitions, namespace assignments, delegated operation, or Scan-based routing. Multiple access endpoints, if provided, identify the same logical registry and do not prescribe its physical hosting topology.

The `credentialSubject` collection and subject-local typed properties are semantically aligned with the W3C model, but the draft does not claim complete conformance. A conforming external representation additionally requires the mandated `@context`, a `type` including `VerifiableCredential`, URL identifiers, and a securing mechanism. External object-versus-array encoding, mappings for Canton-native `Party` and `ContractId` values, JSON-LD interpretation of `AV_List` and `AV_Map`, context processing, and extension profiles for concerns such as credential status, schema, and evidence remain outside or open.

## Iteration Roadmap

This roadmap is non-normative. Candidate future iterations are not current behavior and require separate design, review, and approval.

* **Current iteration:** provides the generic credential and registry capabilities, discovery declarations, registration access and retrieval APIs, registration expiry, and fee behavior defined above. The DSO profile has one logical registry, with one DSO Party collectively controlled by all SVs serving as both administrator and operator.
* **Candidate future scaling and partitioning:** may evaluate registry partitions, namespace assignment, migration, routing history, and replication or consistency models. Any such model requires a future specification and must define its correctness and operational boundaries.
* **Candidate future delegated operation:** may evaluate Validator-operated infrastructure, delegation and authorization, incentives, accountability, and operational requirements. Evaluation does not imply adoption; delegated operation requires a future specification.
* **Candidate future access and routing evolution:** may evaluate Scan integration, proxies, route selection, multiple endpoints, high availability, and BFT reads. These are possible designs rather than current requirements and require a future specification.
* **Candidate future credential lifecycle and status:** may specify credential revocation, suspension and resumption, with explicit Party-based authorization when the issuer identifier may be external text; decide whether lifecycle state replaces the canonical credential contract or uses a separate lifecycle/status contract; distinguish revocation and suspension from intrinsic `validUntil` expiry, holder relinquishment, Daml archival, and registry removal or deregistration; define interaction with consuming holder replacement; and reconsider holder-authorized archival. Frequent independently mutable or holder-private relationships may motivate separate holder-association contracts, which are not part of this iteration. This requires future specification and approval.
* **Other deferred work:** includes private encrypted holder storage, a full W3C VC profile or Canton DID method, DID-based service discovery and migration rules, and complete issuance, presentation, and selective-disclosure protocols.

## Backwards Compatibility

This iteration is semantically breaking relative to the earlier draft interfaces and prototypes. `CredentialView` loses registry fields, public fetch moves to `RegisteredCredential`, and the candidate factory is renamed and narrowed to registered credentials. In addition, `credentialSubject` changes from one record to `NonEmpty CredentialSubject`; its optional text identifier becomes an optional shared `W3C_VC_Identifier` ADT with `W3C_VC_Identifier_Party Party` and `W3C_VC_Identifier Text` variants, which is also used by `CredentialView.issuer`; `holders` becomes `NonEmpty Party`; generic issuer archival is removed in favor of profile-defined explicit Party authorization where needed; the subject map field is renamed from `properties` to `claims`; each claim entry flattens to a direct property on the external W3C subject object without serializing a `claims` wrapper; the custom `Claims` and `Claim` wrappers remain removed; and `validFrom` and `validUntil` move to top-level `CredentialView`. Credential `id` and `credentialTypes` are also explicit top-level fields. The checked-in candidate interface source now provides the directly consultable model, including the recommended registry factory surface. Using the imported value type adds a direct dependency on `splice-api-token-metadata-v1`; package-version changes can alter generated APIs. Existing prototype/reference Daml implementations, code-generated bindings, and clients that construct, project, query, serialize, or exercise the previous surfaces must migrate together; external profiles must also define contexts, the one-object versus array encoding, issuer and value identifier mappings, and recursive mappings for maps and lists.

Because this document remains `Early Draft` with `CIP TBD`, the split can be made in the draft before standardization. That status does not make it additive and does not eliminate migration work. Implementations may provide temporary aliases or adapters for the old `CredentialFactory` names, but this CIP does not specify a concrete V2 package until the package and SDK compile spike is completed.

We expect that the future CIP that standardizes CNS (potentially including identity verification) will also be constructed such that:

* CNS 1.0 entries are properly integrated; and
* existing name issuance and identity verification services can integrate into the unified system.

## Reference Implementation

The checked-in [`interfaces/`](interfaces/) project contains the candidate Daml interface source used by this CIP. It intentionally leaves production issuer revocation, registry removal, governance, and concrete authorization to profiles.

The non-normative [`demo/`](demo/) is a local PQS-backed reference implementation. It builds a concrete Daml template against the candidate interfaces, allocates synthetic parties, submits deterministic credentials through ledger commands, runs PQS 3.4.1 with PostgreSQL, and exposes a small Java 21/Javalin JDBC API. Run it with `make build`, `make up`, `make daml-test`, `make seed`, and `make smoke-test` from `demo/`.

The in-process Daml seed/test verifies the 360-credential fixture. A partial live PQS run verified 36 `Credential` and 36 `RegisteredCredential` projections. A fresh full 300+ live validation was blocked by intermittent Canton synchronizer readiness with `PACKAGE_SERVICE_CANNOT_AUTODETECT_SYNCHRONIZER`; the complete 360-record PQS/API path is not claimed as fully live-validated.

The demo uses a deliberately simple authority model, public synthetic data, no production authentication, one sandbox participant, and offset pagination without snapshot consistency. It is evidence for the shape of the candidate flow, not a production registry, security profile, endorsement, or resolution of open governance questions. See the [demo README](demo/README.md) for pinned versions, verified limitations, and troubleshooting.

An earlier HTTP and Daml implementation is available on [this Splice PR](https://github.com/hyperledger-labs/splice/pull/3416); it predates the base/registry split and remains historical reference input.

## Copyright

This CIP is licensed under CC0-1.0: [Creative Commons CC0 1.0 Universal](https://creativecommons.org/publicdomain/zero/1.0/)

## Changelog

Sep 18, 2026: standardized holder self-removal as a consuming holder-controlled replacement that preserves credential and registration state, rejects non-holders and final-holder removal, retains remaining holder order, and leaves concrete creation authorization profile-specific

Sep 18, 2026: made `CredentialSubject.id` optional while retaining non-empty subjects, claims without IDs, reserved `id` claim handling, present-only identifier validation and lookup, and explicit issuer, subject, holder, controller, and authorization independence

Sep 17, 2026: required at least one holder for each canonical Credential contract, required holder changes to replace rather than mutate the base view in place, required deterministic duplicate-free holder ordering and template-level stakeholder visibility, retained jointly authorized all-holder archival, and required profiles needing issuer-authorized archival or revocation to define explicit Party authorization

Sep 17, 2026: introduced the shared `W3C_VC_Identifier` representation for issuer and optional subject identity, with Canton-native Party and profile-permitted validated External variants, and clarified that issuer, subject, and holder roles remain independent

Sep 16, 2026: split the intrinsic base Credential from the separate RegisteredCredential registry capability, reframed factory and HTTP APIs around registration, documented deployment modes and breaking draft compatibility, and updated the DSO shared-registry profile

Sep 15, 2026: reorganized the draft into two generic specification layers and a concrete DSO deployment profile, separated generic and DSO-specific policy, and preserved unresolved issues for follow-up

Jan 9, 2026: wrote first draft

## Appendix: Open Issues and Deferred Decisions

The following comment threads remain unresolved or require specification work. They are preserved here until their decisions are incorporated into the normative text.

### Open Comment Threads

Historical comment threads are retained for traceability. Resolved decisions are marked explicitly; the remaining issues stay open for follow-up.

#### Subject Property Data Model (resolved direction)

The current iteration selects `NonEmpty CredentialSubject`, enforcing `1..n` subjects in the interface type. Each subject has `id : Optional W3C_VC_Identifier`, using either `W3C_VC_Identifier_Party Party` for a Canton-native Party or `W3C_VC_Identifier Text` for a profile-permitted and validated external identifier when present, plus `claims : TextMap Api.Token.MetadataV1.AnyValue`. A subject may carry claims without an ID. Every claim entry flattens to a direct property on the external W3C `credentialSubject` object, and no literal `claims` wrapper is serialized. Multi-valued claims use `AV_List`, while the reserved `id` remains outside the map and MUST be rejected as a claim key even when the optional ID is absent. The triple and wrapper alternatives are historical context rather than active options. The checked-in candidate interface source records this model. Open work includes arbitrary nested JSON-LD value mapping, serialization and context rules, the concrete Party-to-URI-or-DID mapping, a named serialization profile if Canton holder metadata is ever exported, and status, schema, and evidence extension profiles.

- **leo@c7.digital (Jan 12, 10:54 PM):** Suggested using a Daml ADT instead of a key-value representation.
- **Simon Meier (Jan 13, 8:41 AM):** Agreed this may be preferable despite JSON ergonomics of key-value maps; planned to evaluate a pure `(subject, property, value)` triple model.
- **leo@c7.digital (Jan 13, 3:49 PM / 3:51 PM):** Noted real cases where duplicate `(subject, property)` entries are useful (aliases, multiple titles).
- **Simon Meier (Jan 13, 4:10 PM):** Agreed that multiset semantics align well with multiple credentials defining the same property for the same subject.

#### Deterministic Resolution and Ordering

- **Vladislav Kokosh (Jan 20, 11:39 PM):** Requested precise rules for last-write-wins and tie-breaking when `createdAt` is optional.
- **Simon Meier (Jan 23, 4:45 PM):** Clarified that:
  - Record time is Canton protocol sequencing time for the transaction confirmation request.
  - Record time is guaranteed to be present on the off-ledger credential registry API.
  - Contract ID is the deterministic tie-breaker (relevant for same-transaction creations).

#### Renewal and Expiry Policy

- **Wayne Collier (Jan 12, 4:12 AM):** Asked whether `expiresAt` supports automatic renewal.
- **Simon Meier (Jan 12, 10:52 AM):** Recommended renewing by creating a new credential ~24h before expiry to reduce contention; automation is intentionally not standardized in this CIP.
- **Frank Preiwuss (Jan 20-21):** Asked who defines expiry policy, whether usage-based extension is possible, and whether payment/deposit mechanisms could support longer lifetime.
- **Simon Meier (Jan 20, 1:12 PM / 4:57 PM):** Clarified registry operator defines policy (DSO likely ~90 days); usage-based extension is conceptually interesting but may add significant complexity and does not remove renewal requirements.
- **Frank Preiwuss (Jan 21, 1:54 PM / 2:02 PM):** Agreed complexity trade-off is significant; payment/deposit model remains a possible direction.

#### Security Model for `expectedRegistryAdmin`

- **Vladislav Kokosh (Jan 21, 12:18 AM):** Requested explicit threat-model language: package vetting/trusted participants are required; the draft's former `expectedAdmin`, now `expectedRegistryAdmin`, is insufficient if implementations are incorrect.
- **Simon Meier (Jan 23, 4:47 PM):** Confirmed this is an implementation constraint to be validated via registry provider security audits and customer DAR vetting.

#### Credential and Registry Lifecycle Boundaries

- Profiles needing issuer-authorized archival or revocation must define an explicit Party authorization mechanism and choice; this iteration does not standardize either operation, and the Party must not be inferred from holders, subjects, claims, or external text.
- Confirm concrete-template signatories and the observer mechanism that makes all designated holders stakeholders when they require normal ledger visibility. Concrete holder self-removal must satisfy replacement-creation authorization without inventing issuer or signatory policy.
- Define precise registry removal, archive, expiry, and status semantics, including authorization and replacement behavior.
- Evaluate separate holder-association contracts only if frequent independently mutable or holder-private relationships require them; they must not bypass `Credential_RemoveSelfAsHolder` or imply relationships between subjects and Parties.
- Keep the checked-in candidate `Credential`, `RegisteredCredential`, and `CredentialRegistryFactory` interface source compile-validated against a compatible SDK and metadata DAR. Concrete templates exposing the relevant interfaces, their signatories, and client projection examples remain future work.

#### Pagination Semantics

- **Resolved for this candidate API:** Bulk retrieval uses bounded, zero-based `page` and `pageSize`, deterministic ordering with a contract-ID tie-breaker, and `hasNext` derived from a `pageSize + 1` query. Offset pagination is not snapshot-consistent; concurrent changes can shift boundaries and produce duplicates or omissions across pages.
- **OpenAPI alignment remains open:** The external credential-registry OpenAPI must define the concrete request and response schema, default and maximum page sizes, supported filters, ordering, and client errors for invalid page values. It must not describe cursor or snapshot guarantees that contradict this candidate API.
- **Vladislav Kokosh (Jan 20, 11:59 PM):** Asked for explicit total ordering to avoid ambiguity when records share the same primary sort value.
- **Simon Meier (Jan 23, 4:48 PM):** Agreed and noted OpenAPI definitions will make this explicit.

#### Encoding Consistency in Examples (resolved)

- **Vladislav Kokosh (Jan 20, 11:40 PM / 11:43 PM):** Pointed out the former inconsistency between triple examples and subject-key encoding.
- **Simon Meier (Jan 23, 4:51 PM):** Proposed uniform triple notation during the earlier discussion.
- **Current decision:** `credentialSubject` has type `NonEmpty CredentialSubject`. Each subject's optional `W3C_VC_Identifier` uses either `W3C_VC_Identifier_Party Party` for a Canton-native Party or `W3C_VC_Identifier Text` for a profile-permitted and validated external identifier when present; a present value maps to `credentialSubject.id`, while `None` omits that property and still permits claims. The same non-optional representation maps `CredentialView.issuer` to the W3C `issuer` with distinct issuer semantics. Each entry in the typed `claims` map flattens to a direct property on that external subject object, without a serialized `claims` wrapper. The `id` key is reserved and rejected in `claims` even when the ID is absent. Multi-valued claims use `AV_List`. An external W3C/JSON-LD profile may encode cardinality one as one object and multiple subjects as an array where its rules permit. Neither an implicit holder subject nor subject suffixes in claim names are used, and no subject, holder, controller, or authorization is inferred from identifiers or claims.

#### KYC Interoperability and Liability

- **Edward Newman (Jan 9, 8:18 PM):** Asked whether third parties can realistically rely on externally issued KYC credentials, especially given legal/regulatory liability concerns.
- **Simon Meier (Jan 12, 8:52 AM / 8:55 AM):** Suggested focusing this CIP on interoperable tooling first; provided examples where shared verification services may emerge (e.g., large organizations with multiple on-ledger parties).
- **Edward Newman (Jan 12, 3:19 PM):** Noted examples may still be intra-entity rather than true cross-entity reliance.
- **Simon Meier (Jan 13, 8:52 AM):** Agreed from a legal perspective; added that technically issuer and consuming app can still be distinct parties/apps.

