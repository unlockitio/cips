package io.unlockit.application.did.manager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.unlockit.application.did.api.DidPathCodec;
import io.unlockit.support.DidTestData;
import io.unlockit.support.TestDidRepository;
import org.junit.jupiter.api.Test;

class DidManagerTest {
    private final DidManager manager = new DidManager(new TestDidRepository());

    @Test
    void separatesIntrinsicAndRegisteredFamilies() {
        assertEquals(2, manager.listDids(null, null).items().size());
        assertEquals(2, manager.listRegisteredDids(null, null, null).items().size());
        assertEquals("did:example:alice", manager.resolve(encoded("did:example:alice")).id().value());
        assertEquals(DidTestData.ALICE_PARTY,
                manager.resolveRegistered(encoded("did:example:alice")).registration().registryAdmin());
    }

    @Test
    void filtersOnlyRegisteredFamilyByParty() {
        assertEquals(1, manager.listRegisteredDids(DidTestData.ALICE_PARTY, null, null).items().size());
        assertEquals(0, manager.listRegisteredDids("unknown::synthetic-party", null, null).items().size());
    }

    @Test
    void paginatesAndRejectsInvalidValues() {
        var page = manager.listDids("0", "1");
        assertEquals(1, page.items().size());
        assertEquals(true, page.hasNext());
        assertThrows(IllegalArgumentException.class, () -> manager.listDids("-1", "50"));
        assertThrows(IllegalArgumentException.class, () -> manager.listDids("0", "101"));
    }

    @Test
    void rejectsInvalidPartyAndUnknownOrInvalidDid() {
        assertThrows(IllegalArgumentException.class, () -> manager.listRegisteredDids(" ", null, null));
        assertThrows(DidManager.DidNotFoundException.class, () -> manager.resolve(encoded("did:example:unknown")));
        assertThrows(IllegalArgumentException.class, () -> manager.resolve("did%3aexample%3aalice"));
        assertThrows(IllegalArgumentException.class, () -> manager.resolve("did%253Aexample%253Aalice"));
        assertThrows(IllegalArgumentException.class, () -> manager.resolve("did%ZZexample"));
    }

    @Test
    void bootstrapIgnoresOtherDidsAndRejectsMissingOrAmbiguousCandidates() {
        var repository = org.mockito.Mockito.mock(io.unlockit.domain.did.repository.DidRepository.class);
        var bootstrap = new DidManager(repository);
        var fixture = new TestDidRepository().findIntrinsicPage(0, 1).getFirst();
        org.mockito.Mockito.when(repository.findDsoCandidates()).thenReturn(java.util.List.of(fixture));
        assertEquals(fixture, bootstrap.resolveDso());
        org.mockito.Mockito.verify(repository, org.mockito.Mockito.never()).findIntrinsicPage(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyInt());
        org.mockito.Mockito.when(repository.findDsoCandidates()).thenReturn(java.util.List.of());
        assertThrows(IllegalStateException.class, bootstrap::resolveDso);
        org.mockito.Mockito.when(repository.findDsoCandidates()).thenReturn(java.util.List.of(fixture, fixture));
        assertThrows(IllegalStateException.class, bootstrap::resolveDso);
        bootstrap.configuredDso = java.util.Optional.of(fixture.id().value());
        org.mockito.Mockito.when(repository.findIntrinsicByDid(fixture.id())).thenReturn(java.util.Optional.of(fixture));
        assertEquals(fixture, bootstrap.resolveDso());
    }

    private static String encoded(String did) { return DidPathCodec.encodeCanonicalSegment(did); }
}
