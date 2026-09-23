package io.unlockit.application.credential.dto;

public record CredentialResponse(
    String contractId,
    String credentialId,
    String lifecycleState,
    CredentialProjection credential) {}
