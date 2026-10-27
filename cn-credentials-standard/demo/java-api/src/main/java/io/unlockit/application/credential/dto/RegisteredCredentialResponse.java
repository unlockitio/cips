package io.unlockit.application.credential.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record RegisteredCredentialResponse(
    String contractId,
    String credentialId,
    String lifecycleState,
    JsonNode credential,
    RegistrationResponse registration,
    JsonNode canton,
    io.unlockit.domain.credential.model.CredentialLifecycle lifecycle,
    @com.fasterxml.jackson.annotation.JsonIgnore String securedSource,
    @com.fasterxml.jackson.annotation.JsonIgnore String securedMediaType) {
  public RegisteredCredentialResponse(String contractId, String credentialId, String lifecycleState,
      JsonNode credential, RegistrationResponse registration, JsonNode canton,
      io.unlockit.domain.credential.model.CredentialLifecycle lifecycle) {
    this(contractId, credentialId, lifecycleState, credential, registration, canton, lifecycle, null, null);
  }
  public RegisteredCredentialResponse(String contractId, String credentialId, String lifecycleState,
      CredentialProjection credential, RegistrationResponse registration) {
    this(contractId, credentialId, lifecycleState, new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(credential),
        registration, null, null);
  }
}
