package io.unlockit.application.credential.dto;

import java.util.List;

public record VerificationResponse(String mechanism, String status, String controller,
    List<String> errors, List<String> warnings, SchemaValidation schemaValidation) {
  public record SchemaValidation(String status, List<String> errors) {}
}
