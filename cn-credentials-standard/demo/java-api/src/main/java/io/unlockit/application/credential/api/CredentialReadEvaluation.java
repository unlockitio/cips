package io.unlockit.application.credential.api;

import io.unlockit.application.credential.dto.CredentialResponse;
import io.unlockit.application.credential.dto.RegisteredCredentialResponse;
import io.unlockit.application.credential.dto.VerificationResponse;
import io.unlockit.application.credential.mapper.OfflineCredentialVerifier;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.MediaType;
import java.nio.charset.StandardCharsets;
import java.time.Clock;

final class CredentialReadEvaluation {
  private static final OfflineCredentialVerifier VERIFIER = new OfflineCredentialVerifier(Clock.systemUTC());

  private CredentialReadEvaluation() {}

  public record Evaluated<T>(T record, VerificationResponse evaluation) {}

  static Response credential(CredentialResponse record) {
    return response(new Evaluated<>(record, evaluate(record.securedSource(), record.securedMediaType())));
  }

  static Response registered(RegisteredCredentialResponse record) {
    return response(new Evaluated<>(record, evaluate(record.securedSource(), record.securedMediaType())));
  }

  private static VerificationResponse evaluate(String source, String mediaType) {
    return VERIFIER.verify(source == null ? new byte[0] : source.getBytes(StandardCharsets.UTF_8),
        mediaType == null ? "unsecured" : mediaType);
  }

  private static Response response(Object entity) {
    return Response.ok(entity, MediaType.APPLICATION_JSON).header("Cache-Control", "no-store").build();
  }
}
