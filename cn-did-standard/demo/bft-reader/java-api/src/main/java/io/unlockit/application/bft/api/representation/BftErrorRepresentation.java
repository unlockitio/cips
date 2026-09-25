package io.unlockit.application.bft.api.representation;

import java.util.List;

public record BftErrorRepresentation(
        int status,
        String error,
        String message,
        List<SourceDiagnosticRepresentation> sources) {
    public BftErrorRepresentation {
        sources = sources == null ? List.of() : List.copyOf(sources);
    }

    public record SourceDiagnosticRepresentation(
            String advertisedUri, int priority, int status, String outcome, String digest, long durationMillis) {}
}
