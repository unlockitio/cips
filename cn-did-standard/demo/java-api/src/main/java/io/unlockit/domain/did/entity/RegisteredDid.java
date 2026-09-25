package io.unlockit.domain.did.entity;

import io.unlockit.domain.did.value_object.PartyId;
import java.util.Optional;

public record RegisteredDid(
        IntrinsicDid intrinsic,
        DidRegistration registration,
        DidDocumentMetadata documentMetadata,
        DidResolutionMetadata resolutionMetadata,
        Optional<PartyId> partyId) {
    public RegisteredDid {
        if (intrinsic == null || registration == null || documentMetadata == null || resolutionMetadata == null) {
            throw new IllegalArgumentException("Intrinsic DID, registration, and metadata are required");
        }
        partyId = partyId == null ? Optional.empty() : partyId;
    }
}
