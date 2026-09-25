package io.unlockit.application.did.api.representation;

import java.time.Instant;

public record DidResolutionMetadataRepresentation(
        String source,
        String integrity,
        Instant retrieved,
        String freshness,
        String finality) {}
