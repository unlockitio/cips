package io.unlockit.application.credential.dto;

public record RegisteredCredentialResponse(
    String contractId,
    String credentialId,
    String lifecycleState,
    CredentialProjection credential,
    RegistrationResponse registration,
    io.unlockit.domain.credential.model.CredentialLifecycle lifecycle) {
  public RegisteredCredentialResponse(String contractId, String credentialId, String lifecycleState,
      CredentialProjection credential, RegistrationResponse registration) {
    this(contractId, credentialId, lifecycleState, credential, registration, null);
  }
}
