package io.unlockit.application.credential.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CredentialProjection(
    @JsonProperty("@context") JsonNode context,
    String id,
    JsonNode type,
    String issuer,
    JsonNode credentialSubject,
    String validFrom,
    String validUntil) {}
