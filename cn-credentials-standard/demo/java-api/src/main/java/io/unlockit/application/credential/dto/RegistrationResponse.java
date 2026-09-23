package io.unlockit.application.credential.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record RegistrationResponse(
    String registryAdmin, String registeredAt, String expiresAt, JsonNode meta) {}
