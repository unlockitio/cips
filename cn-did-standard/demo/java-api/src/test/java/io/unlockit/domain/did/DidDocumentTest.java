package io.unlockit.domain.did;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.unlockit.domain.did.entity.Did;
import io.unlockit.domain.did.entity.DidDocument;
import io.unlockit.domain.did.entity.DidDocumentMetadata;
import io.unlockit.domain.did.entity.DidResolutionMetadata;
import io.unlockit.domain.did.entity.ServiceEntry;
import io.unlockit.domain.did.entity.VerificationMethod;
import io.unlockit.domain.did.entity.VerificationRelationships;
import io.unlockit.domain.did.value_object.DidValue;
import io.unlockit.support.DidTestData;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class DidDocumentTest {
    @Test
    void fixtureIsConsistentAndCollectionsAreImmutable() {
        Did fixture = DidTestData.fixtures().getFirst();
        assertEquals(fixture.id(), fixture.document().id());
        assertThrows(UnsupportedOperationException.class, () -> fixture.document().controller().add(fixture.id()));
        assertThrows(UnsupportedOperationException.class, () -> fixture.document().service().clear());
    }

    @Test
    void defensivelyCopiesCollections() {
        Did fixture = DidTestData.fixtures().getFirst();
        ArrayList<DidValue> controllers = new ArrayList<>(fixture.document().controller());
        DidDocument copied = new DidDocument(fixture.id(), controllers, List.of(), null, List.of());
        controllers.clear();
        assertEquals(1, copied.controller().size());
    }

    @Test
    void rejectsNoControllerDuplicateIdsAndUnresolvedLocalReferences() {
        Did fixture = DidTestData.fixtures().getFirst();
        DidValue id = fixture.id();
        VerificationMethod method = new VerificationMethod(id + "#test-key", "TestKey", id, "test-public-key");
        assertThrows(IllegalArgumentException.class, () -> new DidDocument(id, List.of(), List.of(), null, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new DidDocument(id, List.of(id), List.of(method, method), null, List.of()));
        ServiceEntry service = fixture.document().service().getFirst();
        assertThrows(IllegalArgumentException.class, () -> new DidDocument(id, List.of(id), List.of(), null, List.of(service, service)));
        assertThrows(IllegalArgumentException.class, () -> new DidDocument(
                id, List.of(id), List.of(method), new VerificationRelationships(List.of("#missing"), null, null, null, null), List.of()));
    }

    @Test
    void rejectsAggregateDocumentIdMismatchAndIncompleteMetadata() {
        Did fixture = DidTestData.fixtures().getFirst();
        assertThrows(IllegalArgumentException.class, () -> new Did(
                new DidValue("did:example:other"), fixture.document(), fixture.documentMetadata(), fixture.resolutionMetadata(), fixture.partyId()));
        assertThrows(IllegalArgumentException.class, () -> new DidDocumentMetadata("", fixture.documentMetadata().updated(), false));
        assertThrows(IllegalArgumentException.class, () -> new DidResolutionMetadata("", "evidence", fixture.resolutionMetadata().retrieved(), "freshness", "finality"));
    }
}
