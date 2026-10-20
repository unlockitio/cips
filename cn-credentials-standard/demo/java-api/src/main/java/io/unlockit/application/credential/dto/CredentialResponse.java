package io.unlockit.application.credential.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record CredentialResponse(
    String contractId,
    String credentialId,
    String lifecycleState,
    CredentialProjection credential,
    JsonNode canton,
    io.unlockit.domain.credential.model.CredentialLifecycle lifecycle) {
  public CredentialResponse(String contractId, String credentialId, String lifecycleState,
      CredentialProjection credential) {
    this(contractId, credentialId, lifecycleState, credential, null, null);
  }
}
