package org.cantonnetwork.credentials;

import com.fasterxml.jackson.databind.JsonNode;

public record CredentialResponse(
    String contractId,
    String credentialId,
    JsonNode issuer,
    String registryAdmin,
    JsonNode credentialTypes,
    JsonNode subjects,
    JsonNode holders,
    String validFrom,
    String validUntil) {}
