package io.unlockit.application.did.api.representation;

import java.time.Instant;

public record DidRegistrationRepresentation(
        String registryAdmin,
        Instant registeredAt,
        Instant updatedAt,
        String versionId,
        boolean deactivated,
        Instant deactivatedAt,
        String source,
        String integrityEvidence,
        String finalityEvidence,
        Object metadata) {}
