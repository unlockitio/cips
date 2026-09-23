package io.unlockit.application.credential.manager;

import io.unlockit.application.credential.dto.CredentialPageResponse;
import io.unlockit.application.credential.dto.CredentialResponse;
import io.unlockit.application.credential.dto.RegisteredCredentialPageResponse;
import io.unlockit.application.credential.dto.RegisteredCredentialResponse;
import io.unlockit.application.credential.mapper.CredentialResponseMapper;
import io.unlockit.domain.credential.exception.CredentialNotFoundException;
import io.unlockit.domain.credential.exception.DuplicateCredentialException;
import io.unlockit.domain.credential.exception.InvalidPaginationException;
import io.unlockit.domain.credential.exception.UnsupportedCapabilityException;
import io.unlockit.domain.credential.model.Credential;
import io.unlockit.domain.credential.model.RegisteredCredential;
import io.unlockit.domain.credential.query.CredentialQueryClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;

@ApplicationScoped
public class CredentialManager {
  static final int DEFAULT_PAGE_SIZE = 50;
  static final int MAX_PAGE_SIZE = 100;

  private final CredentialQueryClient queryClient;
  private final CredentialResponseMapper mapper;

  @Inject
  public CredentialManager(CredentialQueryClient queryClient, CredentialResponseMapper mapper) {
    this.queryClient = queryClient;
    this.mapper = mapper;
  }

  public CredentialResponse findByCredentialId(String credentialId, String lifecycleScope) {
    requireActiveLifecycle(lifecycleScope);
    return mapper.toResponse(requireOne(queryClient.findCredentialById(credentialId), credentialId));
  }

  public CredentialResponse findByCredentialId(String credentialId) {
    return findByCredentialId(credentialId, null);
  }

  public CredentialPageResponse findPage(
      String pageValue, String pageSizeValue, String lifecycleScope) {
    requireActiveLifecycle(lifecycleScope);
    PageRequest page = pageRequest(pageValue, pageSizeValue);
    List<Credential> rows = queryClient.findCredentialPage(page.offset(), page.fetchSize());
    return new CredentialPageResponse(
        rows.stream().limit(page.pageSize()).map(mapper::toResponse).toList(),
        page.page(),
        page.pageSize(),
        rows.size() > page.pageSize());
  }

  public CredentialPageResponse findPage(String pageValue, String pageSizeValue) {
    return findPage(pageValue, pageSizeValue, null);
  }

  public RegisteredCredentialResponse findRegisteredByCredentialId(
      String credentialId, String lifecycleScope) {
    requireActiveLifecycle(lifecycleScope);
    return mapper.toResponse(
        requireOne(queryClient.findRegisteredCredentialById(credentialId), credentialId));
  }

  public RegisteredCredentialResponse findRegisteredByCredentialId(String credentialId) {
    return findRegisteredByCredentialId(credentialId, null);
  }

  public RegisteredCredentialPageResponse findRegisteredPage(
      String pageValue, String pageSizeValue, String lifecycleScope) {
    requireActiveLifecycle(lifecycleScope);
    PageRequest page = pageRequest(pageValue, pageSizeValue);
    List<RegisteredCredential> rows =
        queryClient.findRegisteredCredentialPage(page.offset(), page.fetchSize());
    return new RegisteredCredentialPageResponse(
        rows.stream().limit(page.pageSize()).map(mapper::toResponse).toList(),
        page.page(),
        page.pageSize(),
        rows.size() > page.pageSize());
  }

  public RegisteredCredentialPageResponse findRegisteredPage(
      String pageValue, String pageSizeValue) {
    return findRegisteredPage(pageValue, pageSizeValue, null);
  }

  public void rejectHistory() {
    throw new UnsupportedCapabilityException("event history is not supported by this deployment");
  }

  private static <T> T requireOne(List<T> matches, String credentialId) {
    if (matches.isEmpty()) {
      throw new CredentialNotFoundException(credentialId);
    }
    if (matches.size() > 1) {
      throw new DuplicateCredentialException(credentialId);
    }
    return matches.getFirst();
  }

  private static void requireActiveLifecycle(String lifecycleScope) {
    if (lifecycleScope != null && !"active".equals(lifecycleScope)) {
      throw new UnsupportedCapabilityException(
          "only lifecycleScope=active is supported by this deployment");
    }
  }

  private static PageRequest pageRequest(String pageValue, String pageSizeValue) {
    long page = parseLong(pageValue, 0, "page");
    int pageSize = parseInteger(pageSizeValue, DEFAULT_PAGE_SIZE, "pageSize");
    if (page < 0) {
      throw new InvalidPaginationException("page must be at least 0");
    }
    if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
      throw new InvalidPaginationException("pageSize must be between 1 and 100");
    }

    try {
      return new PageRequest(page, pageSize, Math.multiplyExact(page, pageSize));
    } catch (ArithmeticException exception) {
      throw new InvalidPaginationException("pagination offset overflows", exception);
    }
  }

  private static int parseInteger(String value, int defaultValue, String name) {
    if (value == null) {
      return defaultValue;
    }
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException exception) {
      throw new InvalidPaginationException(name + " must be an integer", exception);
    }
  }

  private static long parseLong(String value, long defaultValue, String name) {
    if (value == null) {
      return defaultValue;
    }
    try {
      return Long.parseLong(value);
    } catch (NumberFormatException exception) {
      throw new InvalidPaginationException(name + " must be an integer", exception);
    }
  }

  private record PageRequest(long page, int pageSize, long offset) {
    int fetchSize() {
      return pageSize + 1;
    }
  }
}
