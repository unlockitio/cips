package io.unlockit.application.credential.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record RegisteredCredentialResponse(
    String contractId,
    String credentialId,
    String lifecycleState,
    CredentialProjection credential,
    RegistrationResponse registration,
    JsonNode canton,
    io.unlockit.domain.credential.model.CredentialLifecycle lifecycle) {
  public RegisteredCredentialResponse(String contractId, String credentialId, String lifecycleState,
      CredentialProjection credential, RegistrationResponse registration) {
    this(contractId, credentialId, lifecycleState, credential, registration, null, null);
  }
}
