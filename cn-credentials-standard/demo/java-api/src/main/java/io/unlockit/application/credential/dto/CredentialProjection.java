package io.unlockit.application.credential.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record CredentialProjection(
    String id,
    JsonNode issuer,
    JsonNode credentialTypes,
    JsonNode credentialSubject,
    JsonNode holders,
    String validFrom,
    String validUntil) {}
