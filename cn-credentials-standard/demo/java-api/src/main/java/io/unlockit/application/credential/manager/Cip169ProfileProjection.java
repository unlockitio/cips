package io.unlockit.application.credential.manager;

import com.fasterxml.jackson.databind.JsonNode;
import io.unlockit.domain.credential.model.RegisteredCredential;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/** Application-side demo projection of the draft profile rules; not a credential lookup endpoint. */
public final class Cip169ProfileProjection {
  public static final List<String> KEYS = List.of(
      "display-name", "description", "keywords", "notice", "avatar", "website", "email",
      "phone", "location", "mail", "social:telegram", "social:x", "social:github",
      "social:linkedin", "social:discord");
  private static final Set<String> WELL_KNOWN = Set.copyOf(KEYS);

  private Cip169ProfileProjection() {}

  public record Section(String issuer, Map<String, JsonNode> claims) {}
  public record Profile(List<Section> sections, Optional<Section> compact) {}
  public record Source(int registryPriority, RegisteredCredential record) {}
  private record Selected(JsonNode value, int priority, Instant recordTime, String contractId) {}

  /** Sources must come from a holder- and keyPrefix-constrained lookup; both constraints are rechecked. */
  public static Profile resolve(String holder, String namespace, Instant at,
      List<String> knownIssuers, List<Source> sources) {
    if (holder == null || holder.isBlank() || namespace == null || namespace.isBlank()
        || at == null || knownIssuers == null || sources == null) {
      throw new IllegalArgumentException("holder, namespace, time, issuer policy and sources are required");
    }
    Map<String, Map<String, Selected>> byIssuer = new TreeMap<>();
    for (Source source : sources) {
      var record = source.record();
      var credential = record.credential();
      var registration = record.registration();
      if (credential.lifecycle() != null && !"active".equals(credential.lifecycle().state())) continue;
      if (!containsHolder(credential.holders(), holder)
          || !inRange(at, credential.validFrom(), credential.validUntil())
          || !inRange(at, null, registration.expiresAt())) continue;
      JsonNode issuerNode = credential.issuer();
      if (!"W3C_VC_Identifier_Party".equals(issuerNode.path("tag").asText())) continue;
      String issuer = issuerNode.path("value").asText();
      Instant recorded = Instant.parse(registration.registeredAt());
      for (JsonNode subject : credential.subjects()) {
        JsonNode claims = subject.path("claims");
        if (!claims.isObject()) continue;
        var fields = claims.fields();
        while (fields.hasNext()) {
          var entry = fields.next();
          String key = entry.getKey();
          if (!key.startsWith(namespace) || key.endsWith("!subject")) continue;
          String shortKey = key.substring(namespace.length());
          if (shortKey.isEmpty()) continue;
          var selected = new Selected(entry.getValue(), source.registryPriority(), recorded,
              credential.contractId());
          Map<String, Selected> claimsForIssuer = byIssuer.computeIfAbsent(issuer, unused -> new TreeMap<>());
          Selected previous = claimsForIssuer.get(shortKey);
          if (previous == null || compare(selected, previous) > 0) claimsForIssuer.put(shortKey, selected);
        }
      }
    }
    List<String> order = new ArrayList<>();
    if (byIssuer.containsKey(holder)) order.add(holder);
    for (String issuer : knownIssuers) {
      if (!order.contains(issuer) && byIssuer.containsKey(issuer)) order.add(issuer);
    }
    for (String issuer : byIssuer.keySet()) {
      if (!order.contains(issuer)) order.add(issuer);
    }
    List<Section> sections = new ArrayList<>();
    for (String issuer : order) {
      Map<String, Selected> selected = byIssuer.get(issuer);
      Map<String, JsonNode> claims = new LinkedHashMap<>();
      for (String key : KEYS) {
        if (selected.containsKey(key)) claims.put(key, selected.get(key).value());
      }
      for (var entry : selected.entrySet()) {
        if (!WELL_KNOWN.contains(entry.getKey())) claims.put(entry.getKey(), entry.getValue().value());
      }
      sections.add(new Section(issuer, Collections.unmodifiableMap(claims)));
    }
    Optional<Section> compact = sections.stream()
        .filter(section -> section.issuer().equals(holder) || knownIssuers.contains(section.issuer()))
        .findFirst();
    return new Profile(List.copyOf(sections), compact);
  }

  private static boolean containsHolder(JsonNode holders, String holder) {
    if (!holders.isArray()) return false;
    for (JsonNode item : holders) if (holder.equals(item.asText())) return true;
    return false;
  }

  private static boolean inRange(Instant at, String from, String until) {
    return (from == null || !at.isBefore(Instant.parse(from)))
        && (until == null || !at.isAfter(Instant.parse(until)));
  }

  private static int compare(Selected candidate, Selected current) {
    int priority = Integer.compare(current.priority(), candidate.priority());
    if (priority != 0) return priority;
    int time = candidate.recordTime().compareTo(current.recordTime());
    return time != 0 ? time : candidate.contractId().compareTo(current.contractId());
  }
}
