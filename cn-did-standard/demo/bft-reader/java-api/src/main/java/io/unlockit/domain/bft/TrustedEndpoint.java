package io.unlockit.domain.bft;

import java.net.URI;

public record TrustedEndpoint(URI advertisedUri, URI internalUri, int priority) {}
