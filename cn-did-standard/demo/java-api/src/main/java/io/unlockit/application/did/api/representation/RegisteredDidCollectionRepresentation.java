package io.unlockit.application.did.api.representation;

import java.util.List;

public record RegisteredDidCollectionRepresentation(
        List<RegisteredDidRepresentation> items, long page, int pageSize, boolean hasNext) {
    public RegisteredDidCollectionRepresentation {
        items = List.copyOf(items);
    }
}
