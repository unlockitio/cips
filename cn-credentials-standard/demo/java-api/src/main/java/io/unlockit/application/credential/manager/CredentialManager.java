package io.unlockit.application.credential.manager;

import io.unlockit.application.credential.dto.*;
import io.unlockit.domain.credential.exception.UnsupportedCapabilityException;
import io.unlockit.domain.credential.query.CredentialFilters;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class CredentialManager {
  private final CredentialListingManager listing;

  @Inject
  public CredentialManager(CredentialListingManager listing) { this.listing = listing; }

  public CredentialResponse findByCredentialId(String id, String state) { return listing.get(id, state); }
  public CredentialResponse findByCredentialId(String id) { return findByCredentialId(id, null); }
  public RegisteredCredentialResponse findRegisteredByCredentialId(String id, String state) {
    return listing.getRegistered(id, state);
  }
  public RegisteredCredentialResponse findRegisteredByCredentialId(String id) {
    return findRegisteredByCredentialId(id, null);
  }
  public CredentialPageResponse findPage(String page, String size, String state) {
    return listing.list(page, size, null, filters(state));
  }
  public CredentialPageResponse findPage(String page, String size) { return findPage(page, size, null); }
  public RegisteredCredentialPageResponse findRegisteredPage(String page, String size, String state) {
    return listing.listRegistered(page, size, null, filters(state));
  }
  public RegisteredCredentialPageResponse findRegisteredPage(String page, String size) {
    return findRegisteredPage(page, size, null);
  }
  public void rejectHistory() {
    throw new UnsupportedCapabilityException("event history is not supported by this deployment");
  }
  private static CredentialFilters filters(String state) {
    return new CredentialFilters(state, null, null, null, null, null, null, null, null, null);
  }
}
