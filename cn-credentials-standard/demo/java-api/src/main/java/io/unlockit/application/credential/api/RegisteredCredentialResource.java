package io.unlockit.application.credential.api;

import io.unlockit.application.credential.dto.RegisteredCredentialPageResponse;
import io.unlockit.application.credential.dto.RegisteredCredentialResponse;
import io.unlockit.application.credential.manager.CredentialListingManager;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

@Path("/v1/registered-credentials")
@Produces(MediaType.APPLICATION_JSON)
public class RegisteredCredentialResource {
  private final CredentialListingManager manager;

  @Inject
  public RegisteredCredentialResource(CredentialListingManager manager) { this.manager = manager; }

  @GET
  @Path("/{credentialId}")
  public RegisteredCredentialResponse findByCredentialId(@PathParam("credentialId") String credentialId,
      @BeanParam CredentialParameters parameters) {
    return manager.getRegistered(credentialId, parameters.state());
  }

  @GET
  public RegisteredCredentialPageResponse findPage(@BeanParam CredentialParameters parameters) {
    return manager.listRegistered(parameters.page, parameters.pageSize, parameters.nextPageToken, parameters.filters(true));
  }
}
