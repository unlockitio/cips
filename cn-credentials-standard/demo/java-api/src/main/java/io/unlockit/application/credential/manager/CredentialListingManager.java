package io.unlockit.application.credential.manager;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.unlockit.application.credential.dto.*;
import io.unlockit.application.credential.mapper.CredentialResponseMapper;
import io.unlockit.domain.credential.exception.*;
import io.unlockit.domain.credential.model.Credential;
import io.unlockit.domain.credential.query.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Clock;
import java.time.Duration;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class CredentialListingManager {
  private final CredentialQueryClient client;
  private final CredentialResponseMapper mapper;
  private final ObjectMapper json;
  private final CredentialPageTokens tokens;

  @Inject
  public CredentialListingManager(CredentialQueryClient client, CredentialResponseMapper mapper,
      ObjectMapper json, @ConfigProperty(name = "credentials.pagination.secret") String secret,
      @ConfigProperty(name = "credentials.pagination.ttl-seconds") long ttl) {
    this.client = client;
    this.mapper = mapper;
    this.json = json;
    this.tokens = new CredentialPageTokens(secret, json, Clock.systemUTC(), Duration.ofSeconds(ttl));
  }

  public CredentialPageResponse list(String page, String size, String token, CredentialFilters filters) {
    var request = request(false, page, size, token, filters);
    var rows = client.findCredentialHistory(filters, request.snapshot(), request.lastId(),
        request.lastContract(), null, request.size() + 1, request.offset());
    boolean more = rows.size() > request.size();
    var items = rows.stream().limit(request.size()).map(mapper::toResponse).toList();
    return new CredentialPageResponse(items, request.page(), request.size(), more,
        Long.toString(request.snapshot()), more ? next(request, rows.get(request.size() - 1)) : null);
  }

  public RegisteredCredentialPageResponse listRegistered(String page, String size, String token, CredentialFilters filters) {
    var request = request(true, page, size, token, filters);
    var rows = client.findRegisteredCredentialHistory(filters, request.snapshot(), request.lastId(),
        request.lastContract(), null, request.size() + 1, request.offset());
    boolean more = rows.size() > request.size();
    var items = rows.stream().limit(request.size()).map(mapper::toResponse).toList();
    return new RegisteredCredentialPageResponse(items, request.page(), request.size(), more,
        Long.toString(request.snapshot()), more ? next(request, rows.get(request.size() - 1).credential()) : null);
  }

  public CredentialResponse get(String id, String state) {
    var filters = scope(state);
    long snapshot = client.latestOffset();
    client.validateOffset(snapshot);
    if (filters.state().equals("all")) {
      var active = client.findCredentialHistory(scope("active"), snapshot, null, null, id, 2, 0);
      if (active.size() > 1) throw new DuplicateCredentialException(id);
      if (!active.isEmpty()) return mapper.toResponse(active.getFirst());
    }
    var rows = client.findCredentialHistory(filters, snapshot, null, null, id,
        filters.state().equals("active") ? 2 : 1, 0);
    if (rows.isEmpty()) throw new CredentialNotFoundException(id);
    if (rows.size() > 1) throw new DuplicateCredentialException(id);
    return mapper.toResponse(rows.getFirst());
  }

  public RegisteredCredentialResponse getRegistered(String id, String state) {
    var filters = scope(state);
    long snapshot = client.latestOffset();
    client.validateOffset(snapshot);
    if (filters.state().equals("all")) {
      var active = client.findRegisteredCredentialHistory(scope("active"), snapshot, null, null, id, 2, 0);
      if (active.size() > 1) throw new DuplicateCredentialException(id);
      if (!active.isEmpty()) return mapper.toResponse(active.getFirst());
    }
    var rows = client.findRegisteredCredentialHistory(filters, snapshot, null, null, id,
        filters.state().equals("active") ? 2 : 1, 0);
    if (rows.isEmpty()) throw new CredentialNotFoundException(id);
    if (rows.size() > 1) throw new DuplicateCredentialException(id);
    return mapper.toResponse(rows.getFirst());
  }

  private static CredentialFilters scope(String state) {
    return new CredentialFilters(state, null, null, null, null, null, null, null, null, null);
  }

  private Request request(boolean registered, String pageValue, String sizeValue, String token, CredentialFilters filters) {
    try {
      int size = sizeValue == null ? 50 : Integer.parseInt(sizeValue);
      var query = new CredentialPageTokens.Query(registered ? "registered-credentials" : "credentials",
          filters.state(), filters.createdFrom(), filters.createdUntil(), filters.archivedFrom(),
          filters.archivedUntil(), filters.issuer(), filters.holder(), filters.credentialSubjectId(),
          filters.keyPrefix(), filters.registryAdmin(), size);
      var continuation = token == null ? null : tokens.verify(token, query);
      long page = pageValue == null ? continuation == null ? 0 : continuation.page() : Long.parseLong(pageValue);
      if (page < 0) throw new IllegalArgumentException("page must be at least 0");
      long offset = Math.multiplyExact(page, size);
      String lastId = null;
      String lastContract = null;
      if (continuation != null && pageValue == null) {
        String[] cursor = json.readValue(continuation.cursor(), String[].class);
        if (cursor.length != 2 || cursor[0] == null || cursor[1] == null) throw new IllegalArgumentException("invalid cursor");
        lastId = cursor[0];
        lastContract = cursor[1];
        offset = 0;
      }
      long snapshot = continuation == null ? client.latestOffset() : Long.parseLong(continuation.snapshotOffset());
      client.validateOffset(snapshot);
      return new Request(query, page, size, snapshot, lastId, lastContract, offset);
    } catch (IllegalArgumentException | ArithmeticException | java.io.IOException e) {
      throw new InvalidPaginationException(e.getMessage(), e);
    }
  }

  private String next(Request request, Credential last) {
    try {
      return tokens.issue(request.query(), Long.toString(request.snapshot()), Math.addExact(request.page(), 1),
          json.writeValueAsString(new String[] {last.credentialId(), last.contractId()}));
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw new IllegalStateException(e); }
  }

  private record Request(CredentialPageTokens.Query query, long page, int size, long snapshot,
      String lastId, String lastContract, long offset) {}
}
