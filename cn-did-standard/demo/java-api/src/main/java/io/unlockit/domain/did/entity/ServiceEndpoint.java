package io.unlockit.domain.did.entity;

import java.net.URI;
import java.util.Objects;

public record ServiceEndpoint(URI uri, int priority) {
    public ServiceEndpoint {
        Objects.requireNonNull(uri, "Service endpoint URI is required");
        if (!uri.isAbsolute()) {
            throw new IllegalArgumentException("Service endpoint must be an absolute URI");
        }
        if (priority < 0) {
            throw new IllegalArgumentException("Service endpoint priority must be non-negative");
        }
    }
}
