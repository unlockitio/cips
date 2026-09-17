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

Define a portable base credential contract standard, an optional credential registry interface and API standard built on that base, and a DSO-governed shared-registry deployment profile for storing, retrieving, discovering, and using registered credentials on the Canton Network.

## Specification

This CIP consists of two generic layers, presented in dependency order:

1. **Credential Contract and Registry Standards** defines two distinct surfaces:
   * the **Base Credential Contract Standard**, for intrinsic credentials that can exist and be used without a registry; and
   * the **Credential Registry Interface and API Standard**, a registry capability built on the base interface, with registry-specific metadata, lifecycle operations, read-only HTTP APIs, and visibility semantics. A conforming registry keeps its backend and APIs within the same responsibility and partition boundary so consumers do not depend on its storage implementation.
2. **Application and Metadata Discovery** standardizes discoverable declarations for applications and services, including credential registries. Through namespaced claims, these declarations describe what is available, where its endpoints are, which capabilities or metadata it supports, how clients interact with it, and how requests are routed. Layer 2 defines how a client that knows an identity, party, or application context can find compatible services and the information needed to invoke them. This CIP uses the Credential Registry Interface and API Standard as the current mechanism for publishing and resolving the declarations, while registries themselves can be discoverable services. The off-ledger Asset Registry API discovery requirements in CIP-56 inform this layer's reusable discovery mechanism.

The standard supports three deployment modes:

* **direct-issued Credential without a registry:** an issuer-specific workflow creates a contract implementing the base `Credential` interface without requiring a registry or registry HTTP API;
* **issuer-operated registry:** the issuer, or infrastructure it controls, adds the optional registry capability and APIs; and
* **third-party or shared registry:** a separate administrator registers credentials for issuers and holders under its own registry policy.

This CIP promotes a **DSO Credential Registry Deployment Profile that leverages both layers**. This shared-registry profile implements Layer 1, including the credential registry interfaces and APIs, and applies Layer 2 for namespaced declarations and discovery, while adding DSO-specific governance, deployment, registration expiry, fee, routing, and operational policies. Its DSO-governed control plane publishes registry partitions and endpoints; SV and assigned Validator operators run partitions, and Scan may implement access and routing. These profile-specific choices are not requirements for every conforming registry and do not introduce a third abstraction layer.

Layer 2 defines the namespaced declaration and resolution model. The DSO profile supplies one governed implementation of the registry and discovery surfaces, including a profile-specific bootstrap and routing model. The following sections provide the details.

### Architecture Overview (Non-Normative)

The following view covers the base credential standard, its optional registry capability, Layer 2 discovery, and the DSO Credential Registry Deployment Profile. It is informative and does not change the semantics of the Daml or HTTP APIs. Credential issuance remains the responsibility of issuer-specific apps and their custom workflows; those workflows may issue a base credential directly or use a registry.

![System context for the Canton Network Credentials Standard, showing the two generic layers and the DSO deployment profile that implements and applies them, together with issuers, holders, administrators, operators, governance, applications, explorers, discovery, and routing.](images/credentials-system-context.png)

*System Context for the standard's two generic layers and their concrete DSO deployment profile. [C4-PlantUML source](images/credentials-system-context.puml).*

### Layer 1: Credential Contract and Registry Standards

The data model and APIs are inspired by the [W3C Verifiable Credentials Data Model](https://www.w3.org/TR/vc-data-model-2.0/). They are intended to serve as fundamental building blocks for use-cases such as direct-issued credentials, self-publishing party profile information, providing issuer-attributed human-readable names for parties, or sharing KYC credentials across applications. To keep the scope of this CIP manageable, this CIP only sketches how to implement such use-cases on top of it, but does not provide normative guidance. See [Use Case Analysis](#use-case-analysis) for more information.

#### Base Credential Contract Standard

A base credential represents the intrinsic relationship between issuer, holder, and claims. It can exist without registration. Its validity interval and claim metadata remain intrinsic to `Claims`; registry administration, registration timestamps, registration expiry, visibility, fees, and registry lifecycle are not part of the base credential semantics.

#### Credential Registry Interface and API Standard

A registry adds a separately identifiable capability on top of a base credential. The `RegisteredCredential` interface requires `Credential`, while keeping its view limited to registration-specific fields. Registry clients project both interfaces when they need both the credential contents and registration metadata. The HTTP APIs, public/restricted/private retrieval policy, registration lifecycle, and registry factory in this CIP belong to this optional standard, not to direct-issued credentials.

#### Scope, Roles, and Responsibilities

This standard is concerned with entities and apps acting in the following roles:

* **credential issuers** issue credentials, identify the policy under which claims were assessed, and select the appropriate visibility class
* **credential holders** hold credentials and agree to their claims
* **credential registry administrators** control independent registry parties, policy, issuer-namespace assignment, and the registry backend and APIs
* **credential registry operators** run infrastructure for an administrator; an operator can be an SV or, where assigned, a Validator
* **network explorers and Scan** discover registry partitions and ingest deliberately public records through the APIs
* **app providers** discover and query registries and decide how credentials influence their UIs and on-ledger workflows
* **app users** use those applications
* **DSO governance** defines the DSO deployment profile and assigns issuer namespaces to administrators; it is a control-plane role, not a universal stakeholder on every credential

Administrator and operator are deliberately distinct. An administrator controls a registry party and its policy. One or more operators may host that party for high availability, but multi-hosting one party does not divide its ACS. Independent administrator parties provide the partitioning boundary.

Entities often act in multiple roles. For example, a [CIP-56 token administrator that publishes off-ledger Asset Registry API endpoints](https://github.com/global-synchronizer-foundation/cips/blob/main/cip-0056/cip-0056.md#off-ledger-api-discovery-and-access) acts both as issuer and holder. Token standard wallets use those endpoints to access the Asset Registry APIs required for transfers and other actions.

An identity verification service may act as issuer and registry operator. Whether its verification status is public or restricted is a policy decision; evidence and supporting documents remain private and outside the public DSO profile.

#### Credential Lifecycle, Expiry, and Renewal

The following diagram shows how the APIs defined in this CIP mediate the interaction between the apps in the different roles. Arrows point from clients to servers of APIs.

![Credential lifecycle distinguishing a direct-issued base credential from the optional registered credential capability and its registration metadata.](images/credentials-lifecycle.png)

*Candidate lifecycle view. [PlantUML source](images/credentials-lifecycle.puml).*

The lifecycle diagram distinguishes direct-issued credentials from interaction between applications and registries. Direct issuance is also conforming and bypasses those registry APIs. Credential holders use custom APIs and UIs to interact with issuers. App users rely on app providers to use the optional standardized registry APIs appropriately in UIs and on-ledger workflows.

Four lifecycle concepts are distinct:

* **credential validity:** `Claims.validFrom` and `Claims.validUntil` describe when the credential's claims are valid;
* **holder archive:** `Credential_ArchiveAsHolder` lets the holder archive the base credential contract and is not issuer revocation;
* **issuer revocation:** the issuer's revocation model remains open and is not defined by this iteration; and
* **registry lifecycle:** registration, registry removal or archive, and `RegistrationMetadata.expiresAt` are governed by registry policy and do not alter intrinsic credential validity.

A registry MAY remove or archive a registration after its registration expiry. Renewal is performed by creating a replacement credential or registration rather than mutating the existing contract, subject to the still-open lifecycle details below.

TODO: confirm the recommendation to create a replacement credential approximately 24 hours before expiry to avoid contention with existing prepared transactions referencing the old credential. The client-side last-write-wins semantics for name resolution supports this approach.

#### Daml Interfaces

The Daml interfaces distinguish the base credential from the optional registration capability. A direct-issued credential implements `Credential`. A registered credential template implements both `Credential` and `RegisteredCredential` because Daml interface `requires` constraints do not automatically compose views, choices, or template interface instances.

The following snippets are **candidate and illustrative**. They have not yet been compiled against the exact target Daml SDK. The `RegisteredCredential requires Credential` dependency is intentional, but the complete choice-method, contract-id, and projection syntax still requires the compile spike; no cast or projection operation between interfaces is defined or assumed here.

##### Credential Interface

The schema of base credentials is adapted from the [Draft PR](https://github.com/hyperledger-labs/splice/pull/3416/changes#diff-808147bf36f1c087a42b92d67d2021c2ca203076cc0e3b6a31f2ccc60497a34d). In the W3C Verifiable Credentials Data Model 2.0, the semantic value of `credentialSubject` is a set of one or more subject objects, and claims are subject-property-value relations. The candidate Daml view therefore keeps the singular field name `credentialSubject` to map the W3C property, but represents its value as `[CredentialSubject]` with conceptual cardinality `1..n`. Daml's list type does not enforce non-emptiness, so implementations MUST reject an empty `credentialSubject` list.

Each `CredentialSubject` contains only the claims about that subject. `CredentialSubject.id` optionally identifies it; `holder` identifies the party holding the credential and is not assumed to equal any credential subject. At an external boundary, a serialization profile may encode cardinality one as one object and multiple subjects as an array where permitted by its W3C/JSON-LD rules. The Daml list does not perform or imply that serialization automatically.

Claim values use the native Daml carrier from `Splice.Api.Token.MetadataV1`, imported qualified below. `AnyValue` supports `AV_Text`, `AV_Int`, `AV_Decimal`, `AV_Bool`, `AV_Date`, `AV_Time`, `AV_RelTime`, `AV_Party`, `AV_ContractId`, `AV_List`, and `AV_Map`. This is a Canton-native typed carrier, not a complete JSON-LD value model. In particular, interoperable serializers MUST map `Party` and `ContractId` values to profile-defined external identifiers and MUST recursively map lists and maps to the target representation. JSON-LD context processing, extension rules, and a securing mechanism remain outside this iteration.

The Daml package has a direct dependency on `splice-api-token-metadata-v1`. Implementations MUST pin a compatible package version: changes to the imported `AnyValue` type or its constructors can require coordinated updates to prototypes, generated code, serializers, and clients.

```haskell
import qualified Splice.Api.Token.MetadataV1 as Api.Token.MetadataV1

data Claim = Claim with
   property : Text
     -- ^ A namespaced property in the form `namespace/property`.
   values : [Api.Token.MetadataV1.AnyValue]
     -- ^ One or more values for this subject/property relation.
  deriving (Eq, Show)

-- | Claims made about one credential subject.
data Claims = Claims with
   values : [Claim]
     -- ^ Properties asserted about the enclosing CredentialSubject.
     --
     -- The namespace `cip-<nr>` is reserved for CIP-defined properties.
     -- All other applications MUST use a Java-style reverse DNS name for a domain under their control.
     -- For example, a property `prop` defined by `example.com` is `com.example/prop`.
     -- Producers SHOULD group values for one property into one Claim. Consumers MUST
     -- accept repeated Claim records with the same property and combine all values
     -- with multiset semantics; list order has no semantic significance.
     -- Implementations MUST reject a Claim whose values list is empty.
   validFrom : Optional Time
     -- ^ The time from which this credential is valid.
   validUntil : Optional Time
     -- ^ The time until which this credential is valid.
   meta : Metadata
     -- ^ Metadata associated with these claims. Used for extensibility.
 deriving (Eq, Show)

-- | One W3C-oriented subject object carried by a credential.
data CredentialSubject = CredentialSubject with
   id : Optional Text
     -- ^ An optional identifier for the subject described by the claims.
   claims : Claims
     -- ^ Claims whose subject is this CredentialSubject only.
 deriving (Eq, Show)

-- | The intrinsic view of a credential, independent of any registry.
data CredentialView = CredentialView with
   issuer : Party
     -- ^ The party that issued the credential.
   holder : Party
     -- ^ The party that holds the credential. It need not equal any credential subject.
   credentialSubject : [CredentialSubject]
     -- ^ One or more W3C-oriented subject objects. Implementations MUST reject an empty list.
 deriving (Eq, Show)

-- | A credential that can exist independently of a registry.
interface Credential where
 viewtype CredentialView

 credential_archiveAsHolderImpl : ContractId Credential -> Credential_ArchiveAsHolder -> Update Credential_ArchiveAsHolderResult

 choice Credential_ArchiveAsHolder : Credential_ArchiveAsHolderResult
   -- ^ Archive this credential contract as the holder.
   --
   -- This is always allowed for the holder and matches the real-world analogue
   -- of them destroying their copy. Archive is not issuer revocation and makes
   -- no statement about another copy or registration.
   --
   -- The view is returned for convenience so that the caller does not need to fetch it ahead of time.
   controller (view this).holder
   do credential_archiveAsHolderImpl this self arg

data Credential_ArchiveAsHolderResult = Credential_ArchiveAsHolderResult with
   archivedCredential : CredentialView
     -- ^ The view of the archived credential.
   meta : Metadata
     -- ^ Additional metadata specific to the archive operation, used for extensibility.
 deriving (Eq, Show)
```

##### Registered Credential Interface

Registration metadata is separate from `CredentialView`:

```haskell
-- Candidate/illustrative: compile against the selected Daml SDK before adoption.
data RegistrationMetadata = RegistrationMetadata with
   registryAdmin : Party
     -- ^ The party administering this registration.
   registeredAt : Optional Time
     -- ^ The time at which the credential was registered.
   expiresAt : Optional Time
     -- ^ The time at which the registration expires under registry policy.
     -- This is independent of Claims.validUntil.
   meta : Metadata
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
       -- ^ The expected party administering the registration. Implementations MUST
       -- validate that it matches the registryAdmin in the registration view.
     actor : Party
       -- ^ The party fetching the known contract.
   controller actor
   do registeredCredential_publicFetchImpl this self arg
```

`RegisteredCredentialView` deliberately does not duplicate `CredentialView`. Daml `requires` preserves the type-level dependency on `Credential` but does not compose interface views or choices. A template representing a registered credential must declare both interface instances, and a client that needs intrinsic and registration fields projects both interfaces independently. The concrete template, its `ensure` clauses and choice implementations, and exact projection calls are intentionally omitted pending the SDK compile spike. Every conforming credential template MUST enforce `not (null credentialSubject)` and non-empty `Claim.values` for every subject; the concrete public-fetch implementation MUST validate `expectedRegistryAdmin == registration.registryAdmin` before returning the registration view.

Registry removal, registration archive, administrator lifecycle, and registration expiry belong to the registry interface or its implementation. Their precise status model and authorization remain deferred; this draft does not present them as a complete revocation mechanism.

Credential registries MAY enforce limits on the credentials they store to avoid operational problems from overly large credentials. The limits are communicated to users via the [Credential Registry Info API](#credential-registry-info-api). See [DSO Credential Registry Limits](#dso-credential-registry-limits) for implementation-limit follow-up for the DSO deployment profile.

##### Credential Registry Factory Interface

The purpose of this optional API is to enable credential issuers and holders to *jointly* create, update, and archive registrations in a third-party credential registry. In this candidate model it creates or updates one contract whose template implements both `Credential` and `RegisteredCredential`; it does not attach an interface dynamically to an existing contract, and it does not make a factory mandatory for creating a base credential. Direct-issued and registered templates both supply the complete non-empty `credentialSubject` collection as part of the base `CredentialView`; registration does not change its cardinality or claim model. The specific workflows for obtaining authorization and determining claims are provided by the issuer and implemented in its credential issuance app.

Implementing this API is optional for credential registries. They CAN decide to support only registry-internal workflows for registering credentials and not offer third-party issuers the right to publish credentials to that registry.

The candidate V2 name is `CredentialRegistryFactory`, with a corresponding `CredentialRegistryFactory_UpdateRegisteredCredentials` choice for bulk updates of registered credentials with the same issuer and holder. This is a semantically breaking rename from the draft `CredentialFactory` / `CredentialFactory_UpdateCredentials` surface. Prototype and reference implementations MAY temporarily retain the old symbols as compatibility aliases or adapters, but this CIP does not invent a concrete V2 package or claim that such compatibility code already exists. A corresponding HTTP API endpoint allows retrieval of the context needed by the selected factory version.

Draft specifications of these two interfaces can be found in the draft PR here:

* [Splice/Api/Credential/RegistryV1.daml](https://github.com/hyperledger-labs/splice/pull/3416/changes#diff-808147bf36f1c087a42b92d67d2021c2ca203076cc0e3b6a31f2ccc60497a34d) (includes the data format of credentials)
* [openapi/credential-registry-v1.yaml](https://github.com/hyperledger-labs/splice/pull/3416/changes#diff-a73145dfdb26770f01b5fc0a9f35c7c34f067584acb6ec16de7e82040df6f835)

###### Expected App Usage of Daml APIs

Applications can fetch a base credential in their Daml workflows through `Credential` and compute its intrinsic `CredentialView`. Direct issuance can operate without discovery when the issuer, holder, and verifier already obtain the credential or relevant references through their workflow. Discovery can still be used later to locate issuer applications, status services, registries, or related services. Direct-issued credentials commonly use restricted stakeholder visibility, but direct issuance does not require a credential to be private. For a registered credential, the implementing template supplies both interface instances; clients separately obtain `CredentialView` and `RegisteredCredentialView` when they need both intrinsic and registration data.

We also expect wallets to be able to list credentials held by their user by asking the user’s validator node for active contracts implementing `Credential`. Wallets can offer the user `Credential_ArchiveAsHolder`, while clearly presenting that holder archive is not issuer revocation. When a registry or issuer application advertises services, wallets can offer the relevant custom dApp as explained in [Application and Metadata Discovery](#application-and-metadata-discovery); direct issuance does not depend on that facility.

##### Optional Registry Read-Only HTTP APIs

The HTTP APIs in this section belong to the optional registry standard. A direct-issued base credential is not required to expose any of them.

###### Credential Registry Info API

The purpose of the credential registry info API is twofold:

1. It enables an asynchronous rollout of newer versions of the credential registry APIs.
2. It informs clients about constraints of the registry (e.g. limits on the number of claims or results).

A draft API is specified in [openapi/credential-registry-v1.yaml](https://github.com/hyperledger-labs/splice/pull/3416/changes#diff-a73145dfdb26770f01b5fc0a9f35c7c34f067584acb6ec16de7e82040df6f835).

###### Credential Lookup API

The purpose of the credential lookup API is to allow a rich set of retrieval operations to be directly implemented on top of any credential registry under the constraint that the indexing overhead for credential registries is manageable.

A draft API is specified in [openapi/credential-registry-v1.yaml](https://github.com/hyperledger-labs/splice/pull/3416/changes#diff-a73145dfdb26770f01b5fc0a9f35c7c34f067584acb6ec16de7e82040df6f835). It supports filtering by holder, multiple issuers, and a key prefix. The resolution of multiple entries for the same key is left to the client of the API. The record time as of which a credential contract was created is provided, which makes it easy to implement a last-write-wins semantics.

By default the API returns the credential and registration views permitted by registry policy. It may also return the contract's `created_event_blob` for explicit contract disclosure into a Daml transaction. The blob authenticates the disclosed contract content, but a blob alone does not prove that the contract is currently active, that the registration has not expired, or that its claims remain valid. After learning and disclosing a known contract through the registry API, a transaction can exercise `RegisteredCredential_PublicFetch` to validate activeness at transaction time and retrieve registration metadata. This choice is not an anonymous public discovery operation. Credential validity and registration expiry remain separate checks, and clients project `Credential` separately for intrinsic fields.

"Public" describes retrieval policy, not automatic Canton visibility. It does not make every contract or event visible to every participant. Applications and explorers discover and query or ingest public records through registry APIs, while Canton transaction visibility and explicit disclosure continue to apply.

###### Bulk Credential Retrieval API

The purpose of the bulk credential retrieval API is to enable network explorers to ingest all public credentials of a registry as they are created, updated, and archived.

The corresponding HTTP endpoints expose the synchronizers used by the registry and pages of public create and archive events in record-time order. Explorers discover all advertised partitions and maintain a cursor per partition; routing and aggregation MUST avoid duplicate records when assignments change.

#### Visibility Modes

Registry administrators classify records as **public**, **restricted**, or **private**. Public records may be returned to unauthenticated API clients and indexed by explorers. Restricted records require an authorization policy. Private records and their contents are not exposed through the public APIs.

#### Security Considerations

The `expectedRegistryAdmin` check depends on interface implementations enforcing the expected registry administrator and on callers obtaining that party from a trusted source. Package vetting and registry-provider security review remain necessary. Apps and users that want to use registered credentials from a specific registry on-ledger must vet the .dars of that credential registry.

<a id="application-and-metadata-discovery"></a>
### Layer 2: Standardized Application and Metadata Discovery

Layer 2 standardizes the current candidate mechanism for discovering applications and services, including credential registries: namespaced claims published and resolved through the Credential Registry Interface and APIs. Following the concrete Asset Registry API discovery need and form informed by CIP-56, a declaration associates a known subject, registry administrator, or application context with endpoint, capability, invocation, and routing metadata. A client that knows that context can query the Credential Registry mechanism to resolve compatible service locations and the information needed to use them. CIP-56 remains the informing reference for Asset Registry API discovery; it is not a DID mechanism or a bootstrap authority for the Credential Registry, and this CIP does not change it.

This model is conceptually similar to [W3C DID resolution](https://www.w3.org/TR/did-resolution/) and DID Document [`service` entries](https://www.w3.org/TR/did-core/#services): DID resolution resolves a known DID to a DID Document, whose `service` entries can advertise service metadata and endpoints. DID Core does not by itself establish endpoint trust, availability, API correctness, or BFT read semantics.

DID-based service discovery is outside this CIP and is neither a dependency nor a conformance requirement. A future Canton Network DID standard or CIP could define it as an alternative or evolution by mapping this layer's service types and declarations to DID Document `service` entries and specifying precedence, coexistence, and migration. Until such a CIP is defined, namespaced claims resolved through the Credential Registry remain this CIP's normative current candidate mechanism.

A concrete deployment must define how a client obtains an initial discovery context or entry point. This bootstrap concern is deployment-specific: Layer 2 does not provide zero-config discovery or define a universal bootstrap authority, and it does not require that the client already know the registry it ultimately discovers or uses. The DSO profile defines one concrete bootstrap below. Direct issuance can operate without this mechanism under the workflow conditions described above, while clients can use discovery separately for related applications and services.

This CIP defines the following namespaced service-declaration keys:

- `cip-TBD/credential-registry-urls`: declares the availability and locations of the off-ledger API for a specific registry administrator party `admin`. The administrator publishes its party, assigned issuer namespaces, API version, supported capabilities, and a list of URLs. Multiple URLs for the same administrator party are replicas for availability or BFT reads. Distinct administrator parties are independent partitions.

- `cip-TBD/credential-issuer-app-url`: declares the availability and location of the dApp for a specific credential issuer party `issuer`. It is self-published by `issuer` according to the registry's publication rules and may be accompanied by claims describing supported interactions, capabilities, or invocation parameters. A wallet can read a user's credentials from the user's node, use the deployment's discovery entry point and Credential Registry mechanism to resolve this claim, and offer a redirect to the issuer-specific dApp. The redirect may specify a `credential-contract-id=<contract-id>` query parameter. Providing this UI remains optional for credential issuers.

In general, claim properties use the form `namespace/property`, where the namespace is either:

1. `cip-<nr>` for properties defined in a CIP; or
2. `<dns-name>` for properties defined by an organization that controls that DNS name.

Layer 2 generalizes this discovery need so individual CIPs can define namespaced declarations for their own service families. Its claim-based mechanism is informed by CIP-56's concrete need and form for off-ledger Asset Registry API discovery, while this CIP defines publication and resolution through Credential Registry APIs.

#### Service and Application Discovery

The application and service discovery problem on the Canton Network arises when a client knows an application, provider, party, or contract context but does not yet know which compatible service is available, where its endpoint is, which capabilities or metadata it supports, or how to invoke it. Layer 2 standardizes namespaced declarations for this information, including declarations for credential registry services.

A typical example is a wallet that knows the registry `admin` party-id on a `Holding` contract owned by its user but does not yet know the compatible off-ledger registry service, its available URLs, or how to use its API. The wallet resolves this information to read token metadata such as total supply and to obtain the data required to transfer the `Holding`.

As described in [Application and Metadata Discovery](#application-and-metadata-discovery), publishing these declarations under well-known namespaced claims lets a client resolve the service and its interaction metadata through this CIP's Credential Registry mechanism. The deployment supplies an initial discovery context or entry point; the [DSO Bootstrap and Discovery](#dso-bootstrap-and-discovery) profile defines one concrete endpoint and namespace-routing model.

##### Application Discovery

The list of all featured applications can be retrieved by querying for the
corresponding credential issued by the `dso` party with property
`cip-TBD/is-featured-app`.
Fetching the public credentials held by their application provider party then allows discovering further meta information about the application.

#### Profile Publication

The problem of profile publication is how to enable the useful functionality of party owners self-publishing well-known metadata (e.g., website, LinkedIn profile, dApp URL) about themselves. This is common functionality in many systems and helps connect the system’s users.

This information is typically unverified, which is fine as long as that information is not used to resolve names, but only to present additional details on a party (e.g., shown on hover).

Such self-published profile information can be published in a credential registry using credentials with issuer = holder and an appropriate claim. For example, the owner of a party `p` could publish their website using a subject object of the form

```text
credentialSubject = [
  CredentialSubject {
    id = Some "<p-identifier>",
    claims = Claims {
      values = [Claim { property = "profile.example/website", values = [Api.Token.MetadataV1.AV_Text "<url>"] }]
    }
  }
]
```

Here the claim is a property about the identified credential subject. The identifier is an application-profile representation of `p`, such as a URI or DID; it is not the Daml `Party` value serialized directly. The fact that this self-published example uses the same entity as holder and subject does not make the ledger holder equal to that or any other credential subject in the general model.

To ensure that different applications interpret profile information the same way, a future CIP should standardize the common properties used in profiles (e.g., by building on the corresponding ENS standard [ENSIP-18](https://docs.ens.domains/ensip/18/)).

Applications may also need to discover registries storing a user’s profile information. A deployment profile can provide routing for this purpose; the DSO profile provides one concrete model, while generic Layer 2 does not require that profile or a preexisting DSO registry.

#### Party Name Resolution

Party-ids are globally unique identifiers used in the Canton Network. However, they are difficult to use for humans as they are neither memorable nor easily comparable. Below we sketch how to build a name resolution system on top of the Credential Registry Interface and API Standard and the DSO deployment profile's routed partitions. We do so in two steps:

1. We explain how to resolve names managed by a single issuer within a single credential registry.
2. We explain how to resolve names across multiple issuers and credential registries.

##### Single Issuer and Single Credential Registry Name Resolution

Functionally this is what CNS 1.0 provides, where the `dso` party is both the issuer and credential registry administrator. A CNS record for user `p` with name `n` corresponds to a credential with:

```text
credentialSubject = [
  CredentialSubject {
    id = Some "<n>",
    claims = Claims {
      values = [Claim { property = "cip-TBD/cns-owner", values = [Api.Token.MetadataV1.AV_Party <p>] }]
    }
  }
]
```

The credential is issued by the `dso` party and held by `p`. We can read this credential as “The `dso` party claims that the CNS name \<n\> is owned by \<p\>”. The CNS name is the identified subject, the `Api.Token.MetadataV1.AV_Party` names a ledger party as the property value, and the holder remains a separate lifecycle role. An external VC profile must map that `Party` value to an identifier/URI/DID. With multiple subject objects, `credentialSubjectId=<n>` matches a credential when at least one subject has that `id`; `keyPrefix` is evaluated against claims of matching subjects.

Resolving a CNS name `n` to the party holding it can be done using:

```text
GET /credential-registry/v1/credentials?issuer=<dso>&credentialSubjectId=<n>&keyPrefix=cip-TBD/cns-owner
```

The response will contain the credential listing the owner of the CNS name if it exists. The guarantee that there is at most one such credential is provided by the `dso` party as the issuer, which shows why it is paramount to constrain the query with `issuer=<dso>`.

The reverse lookup to query all names of a party `p` can be done using:

```text
GET /credential-registry/v1/credentials?holder=<p>&issuer=<dso>&keyPrefix=cip-TBD/cns-owner
```

The response will list one credential per CNS name assigned to the holder.

The [draft PR shows here](https://github.com/hyperledger-labs/splice/pull/3416/changes#diff-6ffb0d08eee67175e91eabd4e3bf1d811e8ab18a029a541d169aa482fe3e294a) the earlier prototype approach of implementing `Credential` directly on the existing `AnsEntry` template to expose CNS 1.0 entries through the Credential Registry API. Under the candidate split in this draft, a registry-exposed template implements both `Credential` and `RegisteredCredential`; the exact migration of `AnsEntry` requires the Daml SDK compile spike. The issuance of CNS 1.0 entries continues to use the existing workflow. This matches the overall design of this CIP, which gives issuers full freedom in their issuance workflows.

##### Multi-Issuer and Multi-Registry Name Resolution

The basic idea is to follow the DNS construction and use hierarchical names and recursive resolution. Resolution is always done against a specific pair of an `issuer` and a registry `admin`. The root of the resolution is CNS with issuer `dso` and registry administrator `dso`.

Managing the issuers associated with root entries (i.e., top-level domain registrars) requires defining suitable off-ledger governance. The association itself can be stored on-ledger as a credential issued by the `dso` party. We expect that such governance can be built by adopting existing policies like the ones from ICANN for the Canton Foundation.

To support deduplicating names across multiple organizations issuing them concurrently, we expect that a more specific name registry interface will need to be developed. It can be implemented without contract keys by having the name registry administrator maintain the key-value map on-ledger in a scalable fashion (e.g., as a radix tree).

The main challenge we see with multi-issuer, multi-registry name resolution is actually not in the name issuance and resolution aspect, but the design problem of how to cleanly allow apps to leverage the existing names that entities have in off-ledger systems like DNS, email, ENS or LEI.

#### Verified Identities

There are many systems that associate names with entities. In particular, DNS names and email addresses are well-known and widely used to identify counter-parties. In contrast to CNS 1.0, these systems have their authoritative data source off-ledger. Thus we cannot expect that statements about party-to-name associations in these systems can be resolved directly on-ledger.

However, we do want to support imports of statements in the form "I \<issuer> have verified that the entity controlling party \<p> also controls name \<n> in system \<S>". We can represent this using credentials issued by `issuer` for holder `p` with:

```text
credentialSubject = [
  CredentialSubject {
    id = Some "<n>",
    claims = Claims {
      values = [Claim { property = "identity.example/<S>-controller", values = [Api.Token.MetadataV1.AV_Party <p>] }]
    }
  }
]
```

This models the verified external name as the subject and the controlling Canton party as a typed property value. It does not collapse the separate holder role into `credentialSubject`, and an external VC profile must define how both the subject identifier and `Party` value map to interoperable identifiers such as URIs or DIDs.

Once represented that in this way these can be used for name resolution in the same way as explained for CNS 1.0 above.

Issuers have full control over the verification they do before issuing such a credential. For example, doing DNS verification using a [DNS challenge](https://letsencrypt.org/docs/challenge-types/#dns-01-challenge). Metadata about the verification done can be represented on the credential as well using additional claims. Care should though be taken to avoid bloating the credential.

App providers can choose which issuers to query for names in what systems to resolve party names in their applications. They can combine multiple naming sources by issuing multiple name resolution queries.

##### Consistent Cross-App Identities

For identities to work consistently across multiple applications it is important that these apps use compatible name resolution strategies, including compatible lists of issuers and registries. We envision that this can be built for Canton Network in a future CIP that standardizes two aspects:

1. How names from different systems are represented in a single namespace as ASCII strings.
2. How to build suitable Canton Foundation governance such that most apps can use the same issuer and registry configuration.

#### KYC Verification Services

KYC verification imports issuer statements about the physical world using processes that vary between organizations. A verification status may be restricted to authorized consumers. Identity details, evidence, and supporting documents are private and outside the public DSO registry.

These statements are relative to the issuer's process. Publication does not establish regulatory quality or legal validity. App providers can consume external KYC services without network-wide standardization. Custom properties should use the DNS name of their defining organization as a prefix; for example, `acme.com/custom-property`.

Organizations can use experience with restricted registries to inform a future CIP without making private data handling part of this iteration.

### DSO Credential Registry Deployment Profile

This CIP promotes a DSO Credential Registry Deployment Profile that leverages both layers. The profile implements Layer 1, including both interfaces on its registered credential records, and applies Layer 2 for namespaced declarations and discovery, while adding DSO-specific governance, deployment, registration expiry, fee, routing, and operational policies. It is a concrete public deployment of the two generic layers, not a third abstraction layer. Its control plane is governed by the DSO, while its data plane is partitioned across multiple independent registry administrator parties operated by SVs and, where assigned, Validators. The DSO party may remain an administrator for a bootstrap partition, but it is not a stakeholder of every credential.

Each administrator party owns the ACS and workload for its assigned issuer namespaces and runs the registry backend together with the standard APIs, side-by-side with the applicable ANS 1.0 APIs. Discovery publishes the namespace-to-administrator assignment and endpoints so clients and explorers route without knowing the implementation. Assignment by issuer namespace is the minimal approach for this iteration. Governance MUST avoid overlapping active assignments, publish changes before they take effect, and retain sufficient routing history for clients to complete pagination.

An administrator party may be multi-hosted by several operators for high availability and distributed control. Those hosts serve the same logical ACS and do not shard it. Cross-party replication is not required.

#### Deployment Architecture, Administration, and Partitioning

![Container view showing generic Daml interfaces and discovery, plus independently administered DSO registry partitions with colocated backends and APIs.](images/credentials-containers.png)

*Container view of the DSO deployment, distinguishing workload partitioning from per-party high availability. [C4-PlantUML source](images/credentials-containers.puml).*


The Registry Info API advertises capabilities, constraints, administrator party, issuer namespaces, and endpoints. Discovery maps an issuer namespace to one independent administrator party and its colocated backend and APIs. Clients and explorers can therefore route transparently without depending on storage topology. Assignment by issuer namespace is the initial deterministic approach; changing assignments is a governance decision and does not require auctions or Concordia.

Three concepts are distinct:

* **partitioning** uses independent administrator parties to divide ACS, indexing, and API workload by issuer namespace;
* **per-party high availability or multi-hosting** places one administrator party on multiple operator nodes, which distributes control and availability but still serves one logical ACS;
* **cross-party replication** copies records between administrator parties and is not required by this iteration.

This deployment architecture applies the generic registry and discovery surfaces defined by Layers 1 and 2. The following subsections define the DSO profile's concrete expiry, routing, bootstrap, and operating model.

#### Concrete Expiry and Paid Extension Policy

By default, there is no CC payment required for registering credential records in a DSO-profile partition. Instead, the partition expires registrations within 90 days, configurable by SV voting, so that traffic from registration and renewal contributes toward storage cost. This is registration expiry and does not set or shorten the intrinsic `Claims.validUntil` value.

##### Extended Expiration Durations

The registry optionally supports extending the registration expiration duration by more than 90 days by burning a CC fee configurable by SV voting. The fee purchases registry retention only; it does not extend credential validity.
This burn is executed by performing a CC transfer to the `cip-112/burn` account defined in
[CIP-112](https://github.com/canton-foundation/cips/blob/main/cip-0112/cip-0112.md#4321-special-account-identifiers-for-mint-and-burn) (Token Standard V2) with the following two extra arguments of `V2.TransferFactory_Transfer`:

- `cip-TBD/extend-credential-expiry-to` set to the new expiration time in `extraArgs.meta`
- `cip-TBD/credential-contract-id` set to the contract-id of the credential in `extraArgs.context`

The payment requires authorization from the sender of the funds and from at least one of the credential issuer or holder.

This authorization policy is chosen to allow credential issuance apps to extend the expiration of a credential on behalf of the issuer or holder, as part of an app's workflow.
For example by creating the credential and extending its expiration in the same
transaction.


#### Deployment Components, Scan Integration, and Routing

The APIs are implemented as follows:

1. The `splice-amulet-name-service` package is extended with two templates as prototyped in [this PR](https://github.com/hyperledger-labs/splice/pull/3416/changes#diff-271a41476c5ed80c77cbe363f39cc58f5f422a6c9991cfc2fa2bd65398802d7e), subject to the breaking draft migration described below.
   1. The `AnsCredentialFactory` template implements the candidate `CredentialRegistryFactory` interface. A prototype retaining `CredentialFactory` requires an explicit compatibility adapter or migration.
   2. The `AnsCredentialRecord` template implements both `Credential` and `RegisteredCredential`; `requires Credential` preserves the interface dependency, while the concrete template must still declare both interface instances.
2. A registry backend operated by an SV or assigned Validator implements [openapi/credential-registry-v1.yaml](https://github.com/hyperledger-labs/splice/pull/3416/changes#diff-a73145dfdb26770f01b5fc0a9f35c7c34f067584acb6ec16de7e82040df6f835) for its administrator party and assigned issuer namespaces.
3. The Scan app proxy served by a Validator implements the same API, discovers the responsible administrator party, routes to that partition's endpoints, and may compare multiple hosts of that party for BFT reads.
4. SV automation maintains DSO-governed discovery records that announce issuer-namespace assignments, administrator parties, and API URLs. A self-published `dso` record may bootstrap discovery, but applications MUST use the advertised assignments rather than assume that `dso` administers every record.

Scan apps hosting the same administrator party expose the same partition state. Querying those Scan apps supports availability and BFT reads; it does not partition or shard that party's ACS. Independent administrator parties are the partitioning mechanism. Explorers discover every public partition and ingest each partition's public stream through its APIs.

Scalability is measured per partition and across the routed registry. Representative load tests should measure ACS growth, Scan ingestion and indexing, API throughput and latency, storage, and operating cost before more complex mechanisms are considered.

#### Economic, Business, and Operational Considerations (Informative)

The DSO profile's expiry, paid extension, CC burn, and governance parameters provide a transparent mechanism for discussing recurring registry activity without claiming a forecast. Let:

* `F` be the default fee in CC for one paid extension event, as configured by governance;
* `P` be the number of parties eligible to publish records;
* `r` be the average number of eligible records per party;
* `z` be the fraction of eligible records adopted into the registry;
* `e` be the average number of paid extension events per adopted record per period;
* `Y = P * r * z * e` be paid extension events per period; and
* `B = F * Y` be CC burned per period.

This is parametric arithmetic, not a prediction. `P` and `r` are counts, `z` is a dimensionless fraction between zero and one, `e` is events per record per stated period, `Y` is events per that period, and `B` is CC per that period. The draft defines no numeric value for these variables. Any use of the model MUST identify the network, period, eligible population, governance-configured `F`, and assumptions behind `r`, `z`, and `e`. Free renewals, policy exemptions, or records that expire without paid extension are outside `Y`.

Token Standard V1 wallets can be used to burn CC to extend the duration of
credentials by encoding the special receipt account and the extension parameters as follows:

- set `transfer.receiver` to the special `cip-112_no-owner::1220000000000000000000000000000000000000000000000000000000000000abcd` party
- set `cip-112/receiver.id` to `cip-112/burn` in `transfer.meta`
- set the other two arguments `cip-TBD/extend-credential-expiry-to` and `cip-TBD/credential-contract-id` as explained above

<a id="dso-bootstrap-and-discovery"></a>
#### DSO Bootstrap and Discovery

This section defines the DSO profile's concrete bootstrap and routing model; it does not make a preexisting registry a requirement of generic Layer 2. The DSO control plane publishes the active namespace assignments that allow clients to discover the concrete registry partitions and endpoints. The `dso` party may publish each assignment as:

```text
credentialSubject = [
  CredentialSubject {
    id = Some "<issuer-namespace>",
    claims = Claims {
      values = [
        Claim {
          property = "cip-TBD/credential-registry-route",
          values = [Api.Token.MetadataV1.AV_Text "<administrator-party>|<api-version>|<url1>,...,<urlN>"]
        }
      ]
    }
  }
]
```

The route claim is a DSO-profile extension that combines the generic `cip-TBD/credential-registry-urls` endpoint information with the profile's issuer-namespace assignment. A conforming DSO implementation SHOULD also expose the generic key for clients that already know the administrator party.

It can also publish profile-specific claims such as `cip-TBD/is-featured-app` for an application provider subject:

```text
credentialSubject = [
  CredentialSubject {
    id = Some "<application-provider>",
    claims = Claims {
      values = [Claim { property = "cip-TBD/is-featured-app", values = [Api.Token.MetadataV1.AV_Bool True] }]
    }
  }
]
```

Each example uses one subject, but the same credential may contain additional subject objects. The application provider may also be the credential holder in this profile, but clients MUST NOT infer that equality with this or any other subject from the subject structure alone.

Clients must first obtain the DSO control-plane endpoint, then resolve assignments and route reads to the selected administrator's APIs. The initial endpoint may be advertised through the network's published Scan URLs at [https://canton.foundation/sv-network-status/](https://canton.foundation/sv-network-status/). Scan is one implementation of this profile-specific access path, not part of generic discovery semantics. URLs within one assignment serve the same administrator party and may support availability or BFT reads. Different administrator parties partition ACS and workload.


<a id="dso-credential-registry-limits"></a>
#### DSO Credential Registry Limits

TODO: inline the explanations from the source code

- for now see the [source code here](https://github.com/hyperledger-labs/splice/pull/3416/changes#diff-271a41476c5ed80c77cbe363f39cc58f5f422a6c9991cfc2fa2bd65398802d7eR105)

## Motivation

Canton Network applications increasingly need to publish and discover public credentials for service discovery, party profiles, name resolution, and identity or KYC integrations. The off-ledger Asset Registry API discovery requirements in CIP-56 provide a related precedent for reusable service discovery, with CIP-56 retaining its own Asset Registry endpoint semantics. A common public credential registry and read APIs allow applications to discover credential-related information consistently and provide reusable building blocks for credentials issued under application-specific policies.

This CIP defines a portable base credential, optional registry APIs, and a public DSO shared-registry deployment profile. In this increment, governance assigns issuer namespaces to independent administrator parties while leaving credential issuance and interpretation to issuers and consuming applications. The `dso` party may bootstrap discovery or administer a partition, but it is not the universal custodian or stakeholder of all public credentials.

## Rationale

<a id="use-case-analysis"></a>
### Use-Case and Design Requirements

The base credential, optional registry APIs, and DSO profile are building blocks for the visibility-classified examples above. The following sketches remain informative: they demonstrate the building blocks but do not standardize issuer verification, legal reliance, or application-specific authorization. Future CIPs may standardize common claims and resolution mechanisms.

#### Visibility Use Cases and Boundaries (Informative)

Short examples are:
* **Public:** token metadata; public service discovery, profiles, and name resolution; and deliberate publication of bond term-sheet metadata. Bond publication should normally contain a content hash, version, effective date, status, and URI. It does not imply that investor allocations or private terms are stored on-ledger.
* **Restricted:** KYC verification status when its issuer and holder do not intend unrestricted publication.
* **Private:** KYC evidence, personal details, and supporting documents, which are outside the public DSO registry.

Publication records who asserted data and makes the disclosed content inspectable. It does not by itself prove truth, regulatory quality, legal validity, or suitability for a consuming application's purpose.


The design therefore separates publication and retrieval capability from the policy and legal decisions made by registry administrators, issuers, holders, and consuming applications. Public, restricted, and private modes are protocol capabilities; concrete DSO publication policy belongs to the deployment profile.

#### Decisions, Alternatives, and Deferred Questions

The current design uses a conceptually non-empty collection of W3C-oriented `CredentialSubject` records and a `[Claim]` model whose values use `Api.Token.MetadataV1.AnyValue`, preserving duplicate-property multiset semantics. The earlier ADT and triple discussion informed this candidate, but detached `(subject, property, value)` triples and subject-suffixed property keys are not selected for this iteration. `AnyValue` provides Canton-native typed values, including recursive lists and maps, but does not by itself provide full JSON-LD semantics. Layer 2 likewise selects namespaced claims resolved through Credential Registry APIs for the current candidate; DID-based discovery is deferred. Conformance and serialization profiles, exact pagination semantics, and liability for cross-organization KYC reliance remain unresolved.

## Scope and Non-Goals

This CIP defines the Base Credential Contract Standard, the Credential Registry Interface and API Standard, Application and Metadata Discovery, and a public DSO Credential Registry Deployment Profile. The first increment includes a registry-independent credential surface plus an optional registry backend and APIs, namespaced discovery declarations, profile-defined routing, and independent administrator-party partitions. Multiple endpoints for one party provide HA or BFT reads, not sharding.

The following are roadmap items, not dependencies of this iteration: private encrypted holder storage and replication; broader Validator participation and operator incentives; Concordia or market-based allocation; a full W3C VC profile or Canton DID method; DID-based service discovery specified by a separate Canton Network DID standard or CIP, including mappings to DID Document `service` entries and precedence, coexistence, and migration rules; complete issuance, presentation, and selective-disclosure protocols; and cross-party credential replication. The `credentialSubject` collection and subject-local typed claims are W3C-oriented, but the draft does not claim complete conformance because external object-versus-array encoding, mappings for Canton-native `Party` and `ContractId` values, JSON-LD interpretation of `AV_List` and `AV_Map`, context processing, extensibility, and the securing mechanism remain outside or open.

## Backwards Compatibility

This iteration is semantically breaking relative to the current draft interface. `CredentialView` loses registry fields, public fetch moves to `RegisteredCredential`, and the candidate factory is renamed and narrowed to registered credentials. In addition, `credentialSubject` changes from one record to a conceptually non-empty list, and claims change from the previous draft's `TextMap Text` scalar representation to typed `[Claim]` records with one-or-more `Api.Token.MetadataV1.AnyValue` values and duplicate-property multiset semantics. Replacing the draft-local claim-value ADT with this imported type adds a direct dependency on `splice-api-token-metadata-v1`; package-version changes can alter generated APIs. Existing prototype/reference Daml implementations, code-generated bindings, and clients that construct, project, query, serialize, or exercise the previous surfaces must migrate together; external profiles must also define the one-object versus array encoding and mappings for Canton-native values, including `Party`, `ContractId`, maps, and lists.

Because this document remains `Early Draft` with `CIP TBD`, the split can be made in the draft before standardization. That status does not make it additive and does not eliminate migration work. Implementations may provide temporary aliases or adapters for the old `CredentialFactory` names, but this CIP does not specify a concrete V2 package until the package and SDK compile spike is completed.

We expect that the future CIP that standardizes CNS (potentially including identity verification) will also be constructed such that:

* CNS 1.0 entries are properly integrated; and
* existing name issuance and identity verification services can integrate into the unified system.

## Reference Implementation

TODO: add additional implementation notes

- a draft of the earlier HTTP + Daml API specs and Daml implementation of the DSO Credential Registry is available on [this PR](https://github.com/hyperledger-labs/splice/pull/3416); it predates the breaking base/registry split and is reference input rather than a conforming implementation of this iteration
- see [here for notes on how to build the indices for listing of credentials](https://github.com/hyperledger-labs/splice/pull/3416/changes#diff-898d544e4b90bc149b606729ed90e3c4452c3eea8bb1b11301d6adedde704f2e)

## Copyright

This CIP is licensed under CC0-1.0: [Creative Commons CC0 1.0 Universal](https://creativecommons.org/publicdomain/zero/1.0/)

## Changelog

Sep 16, 2026: split the intrinsic base Credential from the optional RegisteredCredential registry capability, reframed factory and HTTP APIs around registration, documented deployment modes and breaking draft compatibility, and updated the DSO shared-registry profile

Sep 15, 2026: reorganized the draft into two generic specification layers and a concrete DSO deployment profile, separated generic and DSO-specific policy, and preserved unresolved issues for follow-up

Jan 9, 2026: wrote first draft

## Appendix: Open Issues and Deferred Decisions

The following comment threads remain unresolved or require specification work. They are preserved here until their decisions are incorporated into the normative text.

### Open Comment Threads

Historical comment threads are retained for traceability. Resolved decisions are marked explicitly; the remaining issues stay open for follow-up.

#### Claims Data Model (candidate decision informed by ADT/triples discussion)

The current iteration selects a conceptually non-empty `[CredentialSubject]`, with each subject holding `[Claim]` records and each claim holding typed `[Api.Token.MetadataV1.AnyValue]` values. Producers SHOULD group values for the same property into one `Claim`; repeated records for that property are also valid and combine with multiset semantics. Subject identity is not encoded in claim keys. Detached `(subject, property, value)` triples and subject-suffixed property keys are superseded for this draft. The historical discussion below is retained because it motivated the selected typed-value and duplicate-property semantics. `AnyValue` supplies Canton-native scalar and recursive carriers, but full W3C conformance, JSON-LD interpretation, and external serialization remain open.

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

- Define the issuer revocation model separately from holder archive; this iteration does not standardize revocation.
- Confirm the exact signatories and observers for direct-issued credential templates.
- Define precise registry removal, archive, expiry, and status semantics, including authorization and replacement behavior.
- Compile the candidate `Credential`, `RegisteredCredential`, and registry factory snippets against the exact target Daml SDK, including template implementations of both required interfaces. Do not standardize package names or projection syntax before that spike.

#### Pagination Semantics

- **Vladislav Kokosh (Jan 20, 11:59 PM):** Asked for explicit total ordering and cursor semantics to avoid gaps/duplicates when many events share the same record time.
- **Simon Meier (Jan 23, 4:48 PM):** Agreed and noted OpenAPI definitions will make this explicit.

#### Encoding Consistency in Examples (resolved)

- **Vladislav Kokosh (Jan 20, 11:40 PM / 11:43 PM):** Pointed out the former inconsistency between triple examples and claim-key subject encoding.
- **Simon Meier (Jan 23, 4:51 PM):** Proposed uniform triple notation during the earlier discussion.
- **Current candidate decision:** `credentialSubject` is a conceptually non-empty subject collection. Each subject's optional `id` identifies only that subject, and its `[Claim]` records contain typed values for properties about that subject, including duplicate-property multiset semantics. Examples show the Daml list shape; an external W3C/JSON-LD profile may encode cardinality one as one object and multiple subjects as an array where its rules permit. Neither an implicit holder subject nor subject suffixes in claim keys are used.

#### KYC Interoperability and Liability

- **Edward Newman (Jan 9, 8:18 PM):** Asked whether third parties can realistically rely on externally issued KYC credentials, especially given legal/regulatory liability concerns.
- **Simon Meier (Jan 12, 8:52 AM / 8:55 AM):** Suggested focusing this CIP on interoperable tooling first; provided examples where shared verification services may emerge (e.g., large organizations with multiple on-ledger parties).
- **Edward Newman (Jan 12, 3:19 PM):** Noted examples may still be intra-entity rather than true cross-entity reliance.
- **Simon Meier (Jan 13, 8:52 AM):** Agreed from a legal perspective; added that technically issuer and consuming app can still be distinct parties/apps.

