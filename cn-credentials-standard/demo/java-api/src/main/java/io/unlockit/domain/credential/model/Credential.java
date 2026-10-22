package io.unlockit.domain.credential.model;

import com.fasterxml.jackson.databind.JsonNode;

public record Credential(
    String contractId,
    String credentialId,
    JsonNode issuer,
    JsonNode credentialTypes,
    JsonNode subjects,
    JsonNode holders,
    JsonNode anchorers,
    String validFrom,
    String validUntil,
    JsonNode credentialSchema,
    JsonNode securing,
    CredentialLifecycle lifecycle) {
  public Credential(String contractId, String credentialId, JsonNode issuer,
      JsonNode credentialTypes, JsonNode subjects, JsonNode holders, JsonNode anchorers,
      String validFrom, String validUntil) {
    this(contractId, credentialId, issuer, credentialTypes, subjects, holders, anchorers,
        validFrom, validUntil, null, null, null);
  }

  public Credential(String contractId, String credentialId, JsonNode issuer,
      JsonNode credentialTypes, JsonNode subjects, JsonNode holders, JsonNode anchorers,
      String validFrom, String validUntil, CredentialLifecycle lifecycle) {
    this(contractId, credentialId, issuer, credentialTypes, subjects, holders, anchorers,
        validFrom, validUntil, null, null, lifecycle);
  }

  public Credential withLifecycle(CredentialLifecycle value) {
    return new Credential(contractId, credentialId, issuer, credentialTypes, subjects,
        holders, anchorers, validFrom, validUntil, credentialSchema, securing, value);
  }
}
