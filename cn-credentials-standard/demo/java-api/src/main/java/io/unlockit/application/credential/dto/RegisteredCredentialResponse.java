package io.unlockit.application.credential.dto;

public record RegisteredCredentialResponse(
    String contractId,
    String credentialId,
    String lifecycleState,
    CredentialProjection credential,
    RegistrationResponse registration) {}
