package io.unlockit.application.credential.api;

import io.unlockit.application.credential.dto.CredentialPageResponse;
import io.unlockit.application.credential.dto.CredentialResponse;
import io.unlockit.application.credential.manager.CredentialListingManager;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

@Path("/v1/credentials")
@Produces(MediaType.APPLICATION_JSON)
public class CredentialResource {
  private final CredentialListingManager manager;

  @Inject
  public CredentialResource(CredentialListingManager manager) { this.manager = manager; }

  @GET
  @Path("/{credentialId}")
  public CredentialResponse findByCredentialId(@PathParam("credentialId") String credentialId,
      @BeanParam CredentialParameters parameters) {
    return manager.get(credentialId, parameters.state());
  }

  @GET
  public CredentialPageResponse findPage(@BeanParam CredentialParameters parameters) {
    return manager.list(parameters.page, parameters.pageSize, parameters.nextPageToken, parameters.filters(false));
  }
}
