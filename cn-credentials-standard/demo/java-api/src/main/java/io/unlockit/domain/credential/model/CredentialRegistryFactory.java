package io.unlockit.domain.credential.model;

public record CredentialRegistryFactory(
    String contractId, String registryAdmin, com.fasterxml.jackson.databind.JsonNode anchorers) {}
