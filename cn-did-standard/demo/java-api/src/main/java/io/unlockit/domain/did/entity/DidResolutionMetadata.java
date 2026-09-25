package io.unlockit.domain.did.entity;

import java.time.Instant;

public record DidResolutionMetadata(
        String source,
        String integrity,
        Instant retrieved,
        String freshness,
        String finality) {
    public DidResolutionMetadata {
        requireText(source, "Resolution source");
        requireText(integrity, "Resolution integrity evidence");
        if (retrieved == null) {
            throw new IllegalArgumentException("Resolution retrieval time is required");
        }
        requireText(freshness, "Resolution freshness evidence");
        requireText(finality, "Resolution finality evidence");
    }

    private static void requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required");
        }
    }
}
