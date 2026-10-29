package io.unlockit.application.credential.manager;

import io.unlockit.application.credential.dto.CredentialTypeCollection;
import io.unlockit.domain.credential.exception.InvalidPaginationException;
import io.unlockit.domain.credential.query.CredentialQueryClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;

@ApplicationScoped
public class CredentialTypeManager {
  private final CredentialQueryClient client;

  @Inject
  public CredentialTypeManager(CredentialQueryClient client) {
    this.client = client;
  }

  public CredentialTypeCollection list(List<String> issuers, String registryAdmin, String stateValue,
      String pageValue, String sizeValue) {
    String state = stateValue == null ? "active" : stateValue;
    if (!List.of("active", "archived", "all").contains(state)
        || registryAdmin != null && registryAdmin.isEmpty()
        || issuers != null && issuers.stream().anyMatch(value -> value == null || value.isEmpty())) {
      throw new InvalidPaginationException("invalid credential-type filter");
    }
    try {
      long page = pageValue == null ? 0 : Long.parseLong(pageValue);
      int size = sizeValue == null ? 50 : Integer.parseInt(sizeValue);
      if (page < 0 || size < 1 || size > 100) {
        throw new InvalidPaginationException("page must be nonnegative and pageSize between 1 and 100");
      }
      long offset = Math.multiplyExact(page, size);
      var rows = client.findCredentialTypes(issuers == null ? List.of() : issuers,
          registryAdmin, state, offset, size + 1);
      return new CredentialTypeCollection(rows.stream().limit(size).toList(), page, size,
          rows.size() > size);
    } catch (NumberFormatException | ArithmeticException exception) {
      throw new InvalidPaginationException("invalid pagination offset or value", exception);
    }
  }
}
