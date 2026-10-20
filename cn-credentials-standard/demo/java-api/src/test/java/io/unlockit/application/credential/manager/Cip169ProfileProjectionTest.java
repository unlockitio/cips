package io.unlockit.application.credential.manager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.unlockit.domain.credential.model.Credential;
import io.unlockit.domain.credential.model.CredentialLifecycle;
import io.unlockit.domain.credential.model.RegisteredCredential;
import io.unlockit.domain.credential.model.Registration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class Cip169ProfileProjectionTest {
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final String NS = "cip-<nr>/";
  private static final Instant NOW = Instant.parse("2026-10-01T00:00:00Z");

  private static JsonNode json(String value) {
    try { return JSON.readTree(value); }
    catch (Exception e) { throw new IllegalArgumentException(e); }
  }

  private static Cip169ProfileProjection.Source source(int priority, String issuer, String holder,
      String id, String recorded, String claims) {
    var credential = new Credential(id, id,
        json("{\"tag\":\"W3C_VC_Identifier_Party\",\"value\":\"" + issuer + "\"}"),
        json("[\"VerifiableCredential\"]"),
        json("[{\"id\":{\"tag\":\"W3C_VC_Identifier_Party\",\"value\":\"" + holder
            + "\"},\"claims\":" + claims + "}]"),
        json("[\"" + holder + "\"]"), json("{\"hd\":\"anchor\",\"tl\":[]}"), null, null);
    return new Cip169ProfileProjection.Source(priority,
        new RegisteredCredential(credential, new Registration("registry", recorded, null, json("{}"))));
  }

  private static Cip169ProfileProjection.Profile resolve(List<Cip169ProfileProjection.Source> sources) {
    return Cip169ProfileProjection.resolve("holder", NS, NOW, List.of("known-b", "known-a"), sources);
  }

  private static String value(Cip169ProfileProjection.Section section, String key) {
    return section.claims().get(key).path("value").asText();
  }

  @Test
  void includesAllFifteenOrderedWellKnownKeysAndKeepsIssuerSectionsSeparate() {
    String[] keys = Cip169ProfileProjection.KEYS.toArray(String[]::new);
    String[] values = {"Alice", "A description", "defi, wallet", "Notice", "https://cdn.example/a.png",
        "https://example.org", "a@example.org", "+15551234567", "New York, USA", "PO Box 1",
        "alice", "aliceX", "alice-git", "alice-link", "alice-discord"};
    var claims = JSON.createObjectNode();
    claims.set(NS + "future-key", json("{\"tag\":\"AV_Text\",\"value\":\"future\"}"));
    for (int i = keys.length - 1; i >= 0; i--) {
      claims.set(NS + keys[i], json("{\"tag\":\"AV_Text\",\"value\":\"" + values[i] + "\"}"));
    }
    var profile = resolve(List.of(
        source(0, "unknown", "holder", "u", "2026-09-01T00:00:00Z",
            "{\"" + NS + "display-name\":{\"tag\":\"AV_Text\",\"value\":\"unknown\"}}"),
        source(0, "known-a", "holder", "a", "2026-09-01T00:00:00Z", claims.toString()),
        source(0, "known-b", "holder", "b", "2026-09-01T00:00:00Z",
            "{\"" + NS + "display-name\":{\"tag\":\"AV_Text\",\"value\":\"known\"}}"),
        source(0, "holder", "holder", "self", "2026-09-01T00:00:00Z",
            "{\"" + NS + "display-name\":{\"tag\":\"AV_Text\",\"value\":\"self\"}}")));
    assertEquals(List.of("holder", "known-b", "known-a", "unknown"),
        profile.sections().stream().map(Cip169ProfileProjection.Section::issuer).toList());
    assertEquals("self", value(profile.compact().orElseThrow(), "display-name"));
    var known = profile.sections().get(2);
    var expectedKeys = new ArrayList<>(Cip169ProfileProjection.KEYS);
    expectedKeys.add("future-key");
    assertEquals(expectedKeys, new ArrayList<>(known.claims().keySet()));
    for (int i = 0; i < keys.length; i++) {
      assertEquals("AV_Text", known.claims().get(keys[i]).path("tag").asText());
      assertEquals(values[i], value(known, keys[i]));
    }
    assertEquals("unknown", value(profile.sections().get(3), "display-name"));
  }

  @Test
  void filtersHolderNamespaceSubjectSuffixAndInactiveRecords() {
    var good = source(0, "known-a", "holder", "good", "2026-09-01T00:00:00Z",
        "{\"" + NS + "display-name\":{\"tag\":\"AV_Text\",\"value\":\"good\"},"
            + "\"" + NS + "email!subject\":{\"tag\":\"AV_Text\",\"value\":\"bad\"},"
            + "\"profile.example/website\":{\"tag\":\"AV_Text\",\"value\":\"bad\"}}");
    var wrongHolder = source(0, "holder", "other", "wrong", "2026-09-01T00:00:00Z",
        "{\"" + NS + "display-name\":{\"tag\":\"AV_Text\",\"value\":\"wrong\"}}");
    var expired = source(0, "known-a", "holder", "expired", "2026-09-02T00:00:00Z",
        "{\"" + NS + "display-name\":{\"tag\":\"AV_Text\",\"value\":\"expired\"}}");
    var early = source(0, "known-a", "holder", "early", "2026-09-03T00:00:00Z",
        "{\"" + NS + "display-name\":{\"tag\":\"AV_Text\",\"value\":\"early\"}}");
    var registryExpired = source(0, "known-a", "holder", "registry-expired", "2026-09-04T00:00:00Z",
        "{\"" + NS + "display-name\":{\"tag\":\"AV_Text\",\"value\":\"registry-expired\"}}");
    var archived = source(0, "known-a", "holder", "archived", "2026-09-05T00:00:00Z",
        "{\"" + NS + "display-name\":{\"tag\":\"AV_Text\",\"value\":\"archived\"}}");
    var profile = resolve(List.of(good, wrongHolder,
        withExpiry(expired, null, "2026-09-30T00:00:00Z", null),
        withExpiry(early, "2026-10-02T00:00:00Z", null, null),
        withRegistrationExpiry(registryExpired, "2026-09-30T00:00:00Z"),
        withExpiry(archived, null, null, "archived")));
    assertEquals(List.of("known-a"), profile.sections().stream().map(Cip169ProfileProjection.Section::issuer).toList());
    assertEquals(Map.of("display-name", good.record().credential().subjects().get(0).path("claims").path(NS + "display-name")),
        profile.sections().get(0).claims());
  }

  @Test
  void selectsRegistryPriorityThenNewestRecordThenContractIdWithoutMergingIssuers() {
    var sources = List.of(
        source(1, "known-a", "holder", "z", "2026-09-30T00:00:00Z", claim("lower-registry")),
        source(0, "known-a", "holder", "a", "2026-09-01T00:00:00Z", claim("high-registry")),
        source(0, "known-a", "holder", "b", "2026-09-02T00:00:00Z", claim("newer")),
        source(0, "known-a", "holder", "c", "2026-09-02T00:00:00Z", claim("tie-c")),
        source(0, "known-b", "holder", "d", "2026-09-03T00:00:00Z", claim("other-issuer")));
    var profile = resolve(sources);
    assertEquals("tie-c", value(profile.sections().get(1), "display-name"));
    assertEquals("other-issuer", value(profile.sections().get(0), "display-name"));
    assertEquals("other-issuer", value(profile.compact().orElseThrow(), "display-name"));
    assertEquals("high-registry", value(resolve(sources.subList(0, 2)).sections().get(0), "display-name"));
  }

  @Test
  void unknownOnlyProfileHasNoCompactFieldsAndSectionsAreDeterministic() {
    var profile = resolve(List.of(source(0, "z-unknown", "holder", "z", "2026-09-01T00:00:00Z", claim("z")),
        source(0, "a-unknown", "holder", "a", "2026-09-01T00:00:00Z", claim("a"))));
    assertEquals(List.of("a-unknown", "z-unknown"),
        profile.sections().stream().map(Cip169ProfileProjection.Section::issuer).toList());
    assertFalse(profile.compact().isPresent());
    assertTrue(resolve(List.of()).sections().isEmpty());
  }

  private static String claim(String value) {
    return "{\"" + NS + "display-name\":{\"tag\":\"AV_Text\",\"value\":\"" + value + "\"}}";
  }

  private static Cip169ProfileProjection.Source withRegistrationExpiry(
      Cip169ProfileProjection.Source source, String until) {
    var r = source.record().registration();
    return new Cip169ProfileProjection.Source(source.registryPriority(),
        new RegisteredCredential(source.record().credential(),
            new Registration(r.registryAdmin(), r.registeredAt(), until, r.meta())));
  }

  private static Cip169ProfileProjection.Source withExpiry(Cip169ProfileProjection.Source source,
      String from, String until, String state) {
    var c = source.record().credential();
    var replacement = new Credential(c.contractId(), c.credentialId(), c.issuer(), c.credentialTypes(),
        c.subjects(), c.holders(), c.anchorers(), from, until,
        state == null ? null : new CredentialLifecycle(state, null, null, null, null, null, null,
            null, null, null, null, null, null));
    return new Cip169ProfileProjection.Source(source.registryPriority(),
        new RegisteredCredential(replacement, source.record().registration()));
  }
}
