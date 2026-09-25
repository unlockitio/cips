package io.unlockit.domain.did.entity;

import io.unlockit.domain.did.value_object.DidValue;
import io.unlockit.domain.did.value_object.PartyId;
import java.time.Instant;
import java.util.Optional;

public record DidRegistration(
        String registryAdmin,
        Instant registeredAt,
        Instant updatedAt,
        String versionId,
        boolean deactivated,
        Instant deactivatedAt,
        String source,
        String integrityEvidence,
        String finalityEvidence,
        Object metadata) {
    public DidRegistration {
        if (registryAdmin == null || registryAdmin.isBlank() || registeredAt == null
                || versionId == null || versionId.isBlank() || source == null || source.isBlank()) {
            throw new IllegalArgumentException("DID registration identity, time, version, and source are required");
        }
    }
}
