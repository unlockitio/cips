package io.unlockit.domain.credential.model;

import com.fasterxml.jackson.databind.JsonNode;

public record Registration(
    String registryAdmin, String registeredAt, String expiresAt, JsonNode meta) {}
