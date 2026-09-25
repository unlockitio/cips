package io.unlockit.domain.did.entity;

import io.unlockit.domain.did.value_object.DidValue;
import io.unlockit.domain.did.value_object.PartyId;
import java.util.Optional;

public record Did(
        DidValue id,
        DidDocument document,
        DidDocumentMetadata documentMetadata,
        DidResolutionMetadata resolutionMetadata,
        Optional<PartyId> partyId) {
    public Did {
        if (id == null || document == null || documentMetadata == null || resolutionMetadata == null) {
            throw new IllegalArgumentException("DID, document, and metadata are required");
        }
        if (!id.equals(document.id())) {
            throw new IllegalArgumentException("DID aggregate and document IDs must match");
        }
        partyId = partyId == null ? Optional.empty() : partyId;
    }
}
