package io.unlockit.application.credential.api;

import io.unlockit.application.credential.dto.CredentialTypeCollection;
import io.unlockit.application.credential.manager.CredentialTypeManager;
import io.unlockit.domain.credential.exception.InvalidPaginationException;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.core.Context;
import java.util.List;

@Path("/v1/credential-types")
@Produces(MediaType.APPLICATION_JSON)
public class CredentialTypeResource {
  private final CredentialTypeManager manager;

  @Inject
  public CredentialTypeResource(CredentialTypeManager manager) {
    this.manager = manager;
  }

  @GET
  public CredentialTypeCollection list(@QueryParam("issuer") List<String> issuers,
      @QueryParam("registryAdmin") String registryAdmin, @QueryParam("state") String state,
      @QueryParam("lifecycleScope") String lifecycleScope, @QueryParam("page") String page,
      @QueryParam("pageSize") String pageSize, @Context UriInfo uri) {
    if (uri.getQueryParameters().containsKey("nextPageToken")
        || uri.getQueryParameters().containsKey("snapshotOffset")) {
      throw new InvalidPaginationException("credential-type pages do not accept snapshot tokens");
    }
    if (state != null && lifecycleScope != null && !state.equals(lifecycleScope)) {
      throw new InvalidPaginationException("conflicting state and lifecycleScope values");
    }
    return manager.list(issuers, registryAdmin, state != null ? state : lifecycleScope, page, pageSize);
  }
}
