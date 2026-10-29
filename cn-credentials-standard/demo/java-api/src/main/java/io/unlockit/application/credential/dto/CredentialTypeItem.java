package io.unlockit.application.credential.dto;

public record CredentialTypeItem(String issuer, String credentialType, String firstObservedAt,
    String lastObservedAt) {}
