package io.unlockit.domain.did.entity;

import java.time.Instant;

public record DidDocumentMetadata(String versionId, Instant updated, boolean deactivated) {
    public DidDocumentMetadata {
        if (versionId == null || versionId.isBlank()) {
            throw new IllegalArgumentException("Document version ID is required");
        }
        if (updated == null) {
            throw new IllegalArgumentException("Document update time is required");
        }
    }
}
