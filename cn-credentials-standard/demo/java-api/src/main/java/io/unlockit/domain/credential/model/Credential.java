package io.unlockit.domain.credential.model;

import com.fasterxml.jackson.databind.JsonNode;

public record Credential(
    String contractId,
    String credentialId,
    JsonNode issuer,
    JsonNode credentialTypes,
    JsonNode subjects,
    JsonNode holders,
    String validFrom,
    String validUntil) {}
