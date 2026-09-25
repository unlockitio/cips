package io.unlockit.domain.bft;

import com.fasterxml.jackson.databind.JsonNode;

public record SourceObservation(
        String advertisedUri,
        int priority,
        int status,
        String outcome,
        String digest,
        JsonNode value,
        long durationMillis) {
    public boolean votes() {
        return (status == 200 || status == 404) && digest != null;
    }
}
