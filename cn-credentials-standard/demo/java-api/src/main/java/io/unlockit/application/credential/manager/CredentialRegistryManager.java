package io.unlockit.application.credential.manager;

import io.unlockit.application.credential.dto.CredentialRegistryPageResponse;
import io.unlockit.application.credential.dto.CredentialRegistryResponse;
import io.unlockit.application.credential.dto.IssuanceFactoryResponse;
import io.unlockit.domain.credential.exception.CredentialRegistryNotFoundException;
import io.unlockit.domain.credential.model.CredentialRegistryFactory;
import io.unlockit.domain.credential.query.CredentialQueryClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Comparator;
import java.util.List;

@ApplicationScoped
public class CredentialRegistryManager {
  static final int DEFAULT_PAGE_SIZE = 50;
  static final int MAX_PAGE_SIZE = 100;
  private static final String API_VERSION = "1.0.0-draft";
  private static final String[] STATES = {"active", "archived", "all"};
  private static final String[] FILTERS = {"createdFrom", "createdUntil", "archivedFrom", "archivedUntil",
      "issuer", "holder", "credentialSubjectId", "keyPrefix"};
  private static final CredentialRegistryResponse.FamilyCapabilities CAPABILITIES =
      new CredentialRegistryResponse.FamilyCapabilities(
          true, true, false, STATES, FILTERS, DEFAULT_PAGE_SIZE, MAX_PAGE_SIZE);
  private static final CredentialRegistryResponse.FamilyCapabilities REGISTERED_CAPABILITIES =
      new CredentialRegistryResponse.FamilyCapabilities(true, true, false, STATES,
          java.util.stream.Stream.concat(java.util.Arrays.stream(FILTERS), java.util.stream.Stream.of("registryAdmin"))
              .toArray(String[]::new), DEFAULT_PAGE_SIZE, MAX_PAGE_SIZE);

  private final CredentialQueryClient queryClient;

  @Inject
  public CredentialRegistryManager(CredentialQueryClient queryClient) {
    this.queryClient = queryClient;
  }

  public CredentialRegistryPageResponse findPage(String pageValue, String pageSizeValue) {
    PageRequest page = pageRequest(pageValue, pageSizeValue);
    List<String> registryIds = queryClient.findCredentialRegistryIds(page.offset(), page.fetchSize());
    List<CredentialRegistryResponse> items =
        registryIds.stream()
            .limit(page.pageSize())
            .map(this::findByRegistryId)
            .toList();
    return new CredentialRegistryPageResponse(
        items, page.page(), page.pageSize(), registryIds.size() > page.pageSize());
  }

  public CredentialRegistryResponse findByRegistryId(String registryId) {
    List<CredentialRegistryFactory> factories =
        queryClient.findCredentialRegistryFactories(registryId);
    if (factories.isEmpty()) {
      throw new CredentialRegistryNotFoundException(registryId);
    }
    List<IssuanceFactoryResponse> issuanceFactories =
        factories.stream()
            .sorted(
                Comparator.comparing(CredentialRegistryFactory::contractId))
            .map(factory -> new IssuanceFactoryResponse(factory.contractId(), factory.anchorers()))
            .toList();
    return new CredentialRegistryResponse(
        registryId, API_VERSION, CAPABILITIES, REGISTERED_CAPABILITIES, issuanceFactories);
  }

  private static PageRequest pageRequest(String pageValue, String pageSizeValue) {
    long page = Pagination.parseLong(pageValue, 0, "page");
    int pageSize = Pagination.parseInteger(pageSizeValue, DEFAULT_PAGE_SIZE, "pageSize");
    return Pagination.pageRequest(page, pageSize, MAX_PAGE_SIZE);
  }

  private record PageRequest(long page, int pageSize, long offset) {
    int fetchSize() {
      return pageSize + 1;
    }
  }

  private static final class Pagination {
    private static PageRequest pageRequest(long page, int pageSize, int maximumPageSize) {
      if (page < 0) {
        throw new io.unlockit.domain.credential.exception.InvalidPaginationException(
            "page must be at least 0");
      }
      if (pageSize < 1 || pageSize > maximumPageSize) {
        throw new io.unlockit.domain.credential.exception.InvalidPaginationException(
            "pageSize must be between 1 and 100");
      }
      try {
        return new PageRequest(page, pageSize, Math.multiplyExact(page, pageSize));
      } catch (ArithmeticException exception) {
        throw new io.unlockit.domain.credential.exception.InvalidPaginationException(
            "pagination offset overflows", exception);
      }
    }

    private static int parseInteger(String value, int defaultValue, String name) {
      if (value == null) {
        return defaultValue;
      }
      try {
        return Integer.parseInt(value);
      } catch (NumberFormatException exception) {
        throw new io.unlockit.domain.credential.exception.InvalidPaginationException(
            name + " must be an integer", exception);
      }
    }

    private static long parseLong(String value, long defaultValue, String name) {
      if (value == null) {
        return defaultValue;
      }
      try {
        return Long.parseLong(value);
      } catch (NumberFormatException exception) {
        throw new io.unlockit.domain.credential.exception.InvalidPaginationException(
            name + " must be an integer", exception);
      }
    }
  }
}
