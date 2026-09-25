package io.unlockit.application.credential.api;

import io.unlockit.domain.credential.exception.InvalidPaginationException;
import io.unlockit.domain.credential.query.CredentialFilters;
import jakarta.ws.rs.QueryParam;

public class CredentialParameters {
  @QueryParam("state") public String state;
  @QueryParam("lifecycleScope") public String lifecycleScope;
  @QueryParam("page") public String page;
  @QueryParam("pageSize") public String pageSize;
  @QueryParam("nextPageToken") public String nextPageToken;
  @QueryParam("createdFrom") public String createdFrom;
  @QueryParam("createdUntil") public String createdUntil;
  @QueryParam("archivedFrom") public String archivedFrom;
  @QueryParam("archivedUntil") public String archivedUntil;
  @QueryParam("issuer") public String issuer;
  @QueryParam("holder") public String holder;
  @QueryParam("credentialSubjectId") public String credentialSubjectId;
  @QueryParam("keyPrefix") public String keyPrefix;
  @QueryParam("registryAdmin") public String registryAdmin;

  public String state() {
    if (state != null && lifecycleScope != null && !state.equals(lifecycleScope))
      throw new InvalidPaginationException("state and lifecycleScope disagree");
    return state == null ? lifecycleScope : state;
  }

  public CredentialFilters filters(boolean registered) {
    if (!registered && registryAdmin != null)
      throw new InvalidPaginationException("registryAdmin requires registered credentials");
    return new CredentialFilters(state(), createdFrom, createdUntil, archivedFrom, archivedUntil,
        issuer, holder, credentialSubjectId, keyPrefix, registryAdmin);
  }
}
