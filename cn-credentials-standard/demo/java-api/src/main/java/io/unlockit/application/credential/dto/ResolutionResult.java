package io.unlockit.application.credential.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ResolutionResult(
    String fqpn,
    @JsonProperty("party_id") String partyId,
    @JsonProperty("display_name") String displayName,
    Map<String, Object> metadata,
    @JsonProperty("valid_until") String validUntil) {}
