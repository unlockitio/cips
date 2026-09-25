package io.unlockit.application.credential.dto;

public record CredentialResponse(
    String contractId,
    String credentialId,
    String lifecycleState,
    CredentialProjection credential,
    io.unlockit.domain.credential.model.CredentialLifecycle lifecycle) {
  public CredentialResponse(String contractId, String credentialId, String lifecycleState,
      CredentialProjection credential) {
    this(contractId, credentialId, lifecycleState, credential, null);
  }
}
