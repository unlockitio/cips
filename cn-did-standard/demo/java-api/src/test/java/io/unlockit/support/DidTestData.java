package io.unlockit.support;

import io.unlockit.domain.did.entity.Did;
import io.unlockit.domain.did.entity.DidDocument;
import io.unlockit.domain.did.entity.DidDocumentMetadata;
import io.unlockit.domain.did.entity.DidRegistration;
import io.unlockit.domain.did.entity.DidResolutionMetadata;
import io.unlockit.domain.did.entity.IntrinsicDid;
import io.unlockit.domain.did.entity.RegisteredDid;
import io.unlockit.domain.did.entity.ServiceEndpoint;
import io.unlockit.domain.did.entity.ServiceEntry;
import io.unlockit.domain.did.entity.VerificationRelationships;
import io.unlockit.domain.did.value_object.DidValue;
import io.unlockit.domain.did.value_object.PartyId;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public final class DidTestData {
    public static final String ALICE_PARTY = "DidDemoAlice::1220-test";
    private static final Instant TIME = Instant.parse("2026-09-21T00:00:00Z");
    private DidTestData() {}

    public static List<IntrinsicDid> intrinsicFixtures() {
        return List.of(intrinsic("alice"), intrinsic("bob"));
    }

    public static List<RegisteredDid> registeredFixtures() {
        return List.of(registered("alice", ALICE_PARTY), registered("bob", "DidDemoBob::1220-test"));
    }

    public static List<Did> fixtures() {
        return registeredFixtures().stream()
                .map(value -> new Did(value.intrinsic().id(), value.intrinsic().document(),
                        value.documentMetadata(), value.resolutionMetadata(), value.partyId()))
                .toList();
    }

    private static IntrinsicDid intrinsic(String label) {
        DidValue id = new DidValue("did:example:" + label);
        DidDocument document = new DidDocument(id, List.of(id), List.of(),
                new VerificationRelationships(List.of(), List.of(), List.of(), List.of(), List.of()),
                List.of(new ServiceEntry(id + "#dids", "CantonDidResolutionService", "1.0",
                        List.of(new ServiceEndpoint(URI.create("http://localhost:42003/v1/dids"), 0),
                                new ServiceEndpoint(URI.create("http://localhost:42004/v1/dids"), 1),
                                new ServiceEndpoint(URI.create("http://localhost:42005/v1/dids"), 2),
                                new ServiceEndpoint(URI.create("http://localhost:42006/v1/dids"), 3)))));
        return new IntrinsicDid("contract-" + label, id, document);
    }

    private static RegisteredDid registered(String label, String party) {
        IntrinsicDid intrinsic = intrinsic(label);
        DidRegistration registration = new DidRegistration(party, TIME, TIME, "demo-version-1", false, null,
                "canton-ledger-pqs-demo", "demo-unverified-integrity-evidence",
                "demo-no-authoritative-finality", java.util.Map.of());
        return new RegisteredDid(intrinsic, registration,
                new DidDocumentMetadata("demo-version-1", TIME, false),
                new DidResolutionMetadata("canton-ledger-pqs-demo", "demo-unverified-integrity-evidence", TIME,
                        "pqs-active-projection-eventual-consistency", "demo-no-authoritative-finality"),
                Optional.of(new PartyId(party)));
    }
}
