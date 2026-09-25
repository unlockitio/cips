package io.unlockit.infrastructure.did.repository;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.unlockit.domain.did.exception.DuplicateDidException;
import io.unlockit.domain.did.value_object.DidValue;
import io.unlockit.infrastructure.did.pqs.PqsDidQueryClient;
import io.unlockit.support.DidTestData;
import java.util.List;
import org.junit.jupiter.api.Test;

class PqsDidRepositoryTest {
    @Test
    void rejectsDuplicateIntrinsicAndRegisteredDids() {
        PqsDidQueryClient client = mock(PqsDidQueryClient.class);
        var intrinsic = DidTestData.intrinsicFixtures().getFirst();
        var registered = DidTestData.registeredFixtures().getFirst();
        when(client.findIntrinsicByDid("did:example:alice")).thenReturn(List.of(intrinsic, intrinsic));
        when(client.findRegisteredByDid("did:example:alice")).thenReturn(List.of(registered, registered));
        var repository = new PqsDidRepository(client);
        var did = new DidValue("did:example:alice");
        assertThrows(DuplicateDidException.class, () -> repository.findIntrinsicByDid(did));
        assertThrows(DuplicateDidException.class, () -> repository.findRegisteredByDid(did));
    }
}
