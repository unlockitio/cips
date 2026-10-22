package io.unlockit.application.credential.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record RegisteredCredentialResponse(
    String contractId,
    String credentialId,
    String lifecycleState,
    JsonNode credential,
    VerificationResponse verification,
    RegistrationResponse registration,
    JsonNode canton,
    io.unlockit.domain.credential.model.CredentialLifecycle lifecycle) {
  public RegisteredCredentialResponse(String contractId, String credentialId, String lifecycleState,
      CredentialProjection credential, RegistrationResponse registration) {
    this(contractId, credentialId, lifecycleState, new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(credential),
        new VerificationResponse("unsecured", "INDETERMINATE", null, java.util.List.of(),
            java.util.List.of("No securing mechanism"), new VerificationResponse.SchemaValidation("Indeterminate", java.util.List.of("No local schema evaluation"))), registration, null, null);
  }
}
