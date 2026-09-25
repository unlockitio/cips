package io.unlockit.application.did.api.representation;

import java.util.List;

public record DidCollectionRepresentation(
        List<DidRepresentation> items, long page, int pageSize, boolean hasNext) {
    public DidCollectionRepresentation {
        items = List.copyOf(items);
    }
}
