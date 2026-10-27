package io.unlockit.application.credential.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record CredentialResponse(
    String contractId,
    String credentialId,
    String lifecycleState,
    JsonNode credential,
    JsonNode canton,
    io.unlockit.domain.credential.model.CredentialLifecycle lifecycle,
    @com.fasterxml.jackson.annotation.JsonIgnore String securedSource,
    @com.fasterxml.jackson.annotation.JsonIgnore String securedMediaType) {
  public CredentialResponse(String contractId, String credentialId, String lifecycleState,
      JsonNode credential, JsonNode canton, io.unlockit.domain.credential.model.CredentialLifecycle lifecycle) {
    this(contractId, credentialId, lifecycleState, credential, canton, lifecycle, null, null);
  }
  public CredentialResponse(String contractId, String credentialId, String lifecycleState,
      CredentialProjection credential) {
    this(contractId, credentialId, lifecycleState, new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(credential), null, null);
  }
}
