package io.unlockit.application.did.api.representation;

import java.time.Instant;

public record DidDocumentMetadataRepresentation(String versionId, Instant updated, boolean deactivated) {}
