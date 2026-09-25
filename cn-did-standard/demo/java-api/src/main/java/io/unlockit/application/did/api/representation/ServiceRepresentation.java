package io.unlockit.application.did.api.representation;

import java.util.List;

public record ServiceRepresentation(String id, String type, ServiceEndpointRepresentation serviceEndpoint) {
    public record ServiceEndpointRepresentation(String version, List<EndpointRepresentation> endpoints) {}

    public record EndpointRepresentation(String uri, int priority) {}
}
