package io.unlockit.application.credential.manager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class CredentialPageTokensTest {
  private static final String SECRET = "test-only-pagination-secret-32-bytes";
  private static final Instant NOW = Instant.parse("2026-09-25T00:00:00Z");

  private CredentialPageTokens tokens(Instant instant, String secret) {
    return new CredentialPageTokens(secret, new ObjectMapper(),
        Clock.fixed(instant, ZoneOffset.UTC), Duration.ofMinutes(5));
  }

  private CredentialPageTokens.Query query(String resource, String state, int size) {
    return new CredentialPageTokens.Query(resource, state, null, null, null, null,
        null, null, null, null, null, size);
  }

  @Test
  void roundTripsBoundContinuation() {
    var service = tokens(NOW, SECRET);
    var query = query("credentials", null, 50);
    var token = service.issue(query, "123", 4, "credential-id/contract-id");
    var decoded = service.verify(token, query);
    assertEquals("active", decoded.query().state());
    assertEquals("123", decoded.snapshotOffset());
    assertEquals(4, decoded.page());
    assertEquals("credential-id/contract-id", decoded.cursor());
    assertEquals(NOW.plusSeconds(300).getEpochSecond(), decoded.expiresAt());
  }

  @Test
  void rejectsTamperingWrongSecretAndExpiry() {
    var service = tokens(NOW, SECRET);
    var query = query("credentials", "all", 50);
    var token = service.issue(query, "123", 1, "cursor");
    assertThrows(IllegalArgumentException.class, () -> service.verify("A" + token, query));
    assertThrows(IllegalArgumentException.class, () -> service.verify(token + "x", query));
    assertThrows(IllegalArgumentException.class,
        () -> tokens(NOW, SECRET + "other").verify(token, query));
    assertThrows(IllegalArgumentException.class,
        () -> tokens(NOW.plusSeconds(300), SECRET).verify(token, query));
  }

  @Test
  void bindsResourceStatePageSizeAndEveryFilter() {
    var service = tokens(NOW, SECRET);
    var original = query("registered-credentials", "all", 50);
    var token = service.issue(original, "123", 1, "cursor");
    for (var changed : java.util.List.of(query("credentials", "all", 50),
        query("registered-credentials", "active", 50),
        query("registered-credentials", "all", 25))) {
      assertThrows(IllegalArgumentException.class, () -> service.verify(token, changed));
    }
    for (int i = 0; i < 9; i++) {
      String[] filters = new String[9];
      filters[i] = "changed";
      var changed = new CredentialPageTokens.Query("registered-credentials", "all",
          filters[0], filters[1], filters[2], filters[3], filters[4], filters[5],
          filters[6], filters[7], filters[8], 50);
      assertThrows(IllegalArgumentException.class, () -> service.verify(token, changed));
    }
  }

  @Test
  void rejectsInvalidConfigurationAndMalformedTokens() {
    assertThrows(IllegalArgumentException.class, () -> tokens(NOW, "short"));
    assertThrows(IllegalArgumentException.class,
        () -> new CredentialPageTokens(SECRET, new ObjectMapper(), Clock.systemUTC(), Duration.ZERO));
    var service = tokens(NOW, SECRET);
    var query = query("credentials", "active", 50);
    for (var invalid : java.util.List.of("", "a.b.c", "a.!!!", "x".repeat(16385))) {
      assertThrows(IllegalArgumentException.class, () -> service.verify(invalid, query));
    }
    assertThrows(IllegalArgumentException.class, () -> service.issue(query, "", 1, "cursor"));
    assertThrows(IllegalArgumentException.class, () -> service.issue(query, "123", 0, "cursor"));
  }
}
