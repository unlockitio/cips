package io.unlockit.domain.did.entity;

import java.util.List;

public record ServiceEntry(String id, String type, String version, List<ServiceEndpoint> endpoints) {
    public ServiceEntry {
        requireText(id, "Service ID");
        requireText(type, "Service type");
        requireText(version, "Service version");
        endpoints = endpoints == null ? List.of() : List.copyOf(endpoints);
        if (endpoints.isEmpty()) {
            throw new IllegalArgumentException("At least one service endpoint is required");
        }
    }

    private static void requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required");
        }
    }
}
