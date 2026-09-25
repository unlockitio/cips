package io.unlockit.application.bft.api.representation;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

public record BftReadRepresentation(
        String family,
        JsonNode result,
        String digest,
        QuorumRepresentation quorum,
        List<SourceDiagnosticRepresentation> sources,
        AuthorityRepresentation authority) {
    public BftReadRepresentation {
        sources = List.copyOf(sources);
    }

    public record QuorumRepresentation(int required, int matched, int total, String agreement) {}

    public record SourceDiagnosticRepresentation(
            String advertisedUri, int priority, int status, String outcome, String digest, long durationMillis) {}

    public record AuthorityRepresentation(String dsoDid, String bootstrapUrl, List<String> advertisedEndpoints) {
        public AuthorityRepresentation {
            advertisedEndpoints = List.copyOf(advertisedEndpoints);
        }
    }
}
