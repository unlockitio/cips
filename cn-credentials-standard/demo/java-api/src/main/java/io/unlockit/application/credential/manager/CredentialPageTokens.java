package io.unlockit.application.credential.manager;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.Objects;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class CredentialPageTokens {
  private static final int MAX_TOKEN_LENGTH = 16384;
  private final byte[] secret;
  private final ObjectMapper mapper;
  private final Clock clock;
  private final Duration lifetime;

  public CredentialPageTokens(String secret, ObjectMapper mapper, Clock clock, Duration lifetime) {
    if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
      throw new IllegalArgumentException("pagination secret must contain at least 32 UTF-8 bytes");
    }
    if (lifetime == null || lifetime.isNegative() || lifetime.isZero()
        || lifetime.getNano() != 0) {
      throw new IllegalArgumentException("token lifetime must be a positive whole number of seconds");
    }
    this.secret = secret.getBytes(StandardCharsets.UTF_8);
    this.mapper = Objects.requireNonNull(mapper);
    this.clock = Objects.requireNonNull(clock);
    this.lifetime = lifetime;
  }

  public record Query(
      String resource, String state, String createdFrom, String createdUntil,
      String archivedFrom, String archivedUntil, String issuer, String holder,
      String credentialSubjectId, String keyPrefix, String registryAdmin, int pageSize) {
    public Query {
      if (!"credentials".equals(resource) && !"registered-credentials".equals(resource)) {
        throw new IllegalArgumentException("invalid resource");
      }
      state = state == null ? "active" : state;
      if (!"active".equals(state) && !"archived".equals(state) && !"all".equals(state)) {
        throw new IllegalArgumentException("invalid state");
      }
      if (pageSize < 1 || pageSize > 100) {
        throw new IllegalArgumentException("pageSize must be between 1 and 100");
      }
      if ("credentials".equals(resource) && registryAdmin != null) {
        throw new IllegalArgumentException("registryAdmin requires registered credentials");
      }
    }
  }

  public record Continuation(
      int version, Query query, String snapshotOffset, long page, String cursor, long expiresAt) {}

  public String issue(Query query, String snapshotOffset, long nextPage, String cursor) {
    var continuation = new Continuation(1, query, snapshotOffset, nextPage, cursor,
        Math.addExact(clock.instant().getEpochSecond(), lifetime.getSeconds()));
    validate(continuation);
    try {
      String payload = encode(mapper.writeValueAsBytes(continuation));
      String token = payload + "." + encode(mac(payload));
      if (token.length() > MAX_TOKEN_LENGTH) {
        throw new IllegalArgumentException("pagination token exceeds maximum length");
      }
      return token;
    } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
      throw new IllegalArgumentException("cannot encode pagination token", exception);
    }
  }

  public Continuation verify(String token, Query expectedQuery) {
    try {
      if (token == null || token.length() > MAX_TOKEN_LENGTH) {
        throw new IllegalArgumentException();
      }
      String[] parts = token.split("\\.", -1);
      if (parts.length != 2 || parts[0].isEmpty()) {
        throw new IllegalArgumentException();
      }
      byte[] signature = Base64.getUrlDecoder().decode(parts[1]);
      if (!encode(signature).equals(parts[1])
          || !MessageDigest.isEqual(mac(parts[0]), signature)) {
        throw new IllegalArgumentException();
      }
      byte[] payload = Base64.getUrlDecoder().decode(parts[0]);
      if (!encode(payload).equals(parts[0])) {
        throw new IllegalArgumentException();
      }
      var continuation = mapper.readValue(payload, Continuation.class);
      validate(continuation);
      if (continuation.expiresAt() <= clock.instant().getEpochSecond()
          || !continuation.query().equals(expectedQuery)) {
        throw new IllegalArgumentException();
      }
      return continuation;
    } catch (IllegalArgumentException | java.io.IOException exception) {
      throw new IllegalArgumentException("invalid or expired nextPageToken", exception);
    }
  }

  private static void validate(Continuation continuation) {
    if (continuation.version() != 1 || continuation.query() == null
        || continuation.snapshotOffset() == null || continuation.snapshotOffset().isBlank()
        || continuation.page() < 1 || continuation.cursor() == null
        || continuation.cursor().isBlank()) {
      throw new IllegalArgumentException("invalid continuation");
    }
  }

  private byte[] mac(String payload) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret, "HmacSHA256"));
      return mac.doFinal(payload.getBytes(StandardCharsets.US_ASCII));
    } catch (java.security.GeneralSecurityException exception) {
      throw new IllegalStateException("HMAC-SHA256 unavailable", exception);
    }
  }

  private static String encode(byte[] value) {
    return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
  }
}
