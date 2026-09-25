package io.unlockit.application.credential.dto;

public record IssuanceFactoryResponse(String contractId, com.fasterxml.jackson.databind.JsonNode anchorers) {}
