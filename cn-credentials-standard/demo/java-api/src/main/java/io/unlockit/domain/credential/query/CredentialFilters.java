package io.unlockit.domain.credential.query;

import io.unlockit.domain.credential.exception.InvalidPaginationException;
import java.time.Instant;
import java.time.format.DateTimeParseException;

public record CredentialFilters(String state, String createdFrom, String createdUntil,
    String archivedFrom, String archivedUntil, String issuer, String holder,
    String credentialSubjectId, String keyPrefix, String registryAdmin) {
  public CredentialFilters {
    state = state == null ? "active" : state;
    if (!java.util.Set.of("active", "archived", "all").contains(state))
      throw new InvalidPaginationException("state must be active, archived or all");
    createdFrom = timestamp(createdFrom);
    createdUntil = timestamp(createdUntil);
    archivedFrom = timestamp(archivedFrom);
    archivedUntil = timestamp(archivedUntil);
    range(createdFrom, createdUntil);
    range(archivedFrom, archivedUntil);
    for (String value : new String[] {issuer, holder, credentialSubjectId, keyPrefix, registryAdmin})
      if (value != null && value.isBlank()) throw new InvalidPaginationException("filters must not be blank");
  }

  private static String timestamp(String value) {
    if (value == null) return null;
    try { return Instant.parse(value).toString(); }
    catch (DateTimeParseException e) { throw new InvalidPaginationException("invalid time filter", e); }
  }

  private static void range(String from, String until) {
    if (from != null && until != null && !Instant.parse(from).isBefore(Instant.parse(until)))
      throw new InvalidPaginationException("time filter From must precede Until");
  }
}
