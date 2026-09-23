package io.unlockit.application.credential.api;

import io.unlockit.application.credential.dto.RegisteredCredentialPageResponse;
import io.unlockit.application.credential.dto.RegisteredCredentialResponse;
import io.unlockit.application.credential.manager.CredentialManager;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

@Path("/v1/registered-credentials")
@Produces(MediaType.APPLICATION_JSON)
public class RegisteredCredentialResource {
  private final CredentialManager manager;

  @Inject
  public RegisteredCredentialResource(CredentialManager manager) {
    this.manager = manager;
  }

  @GET
  @Path("/{credentialId}")
  public RegisteredCredentialResponse findByCredentialId(
      @PathParam("credentialId") String credentialId,
      @QueryParam("lifecycleScope") String lifecycleScope) {
    return manager.findRegisteredByCredentialId(credentialId, lifecycleScope);
  }

  @GET
  public RegisteredCredentialPageResponse findPage(
      @QueryParam("page") String page,
      @QueryParam("pageSize") String pageSize,
      @QueryParam("lifecycleScope") String lifecycleScope) {
    return manager.findRegisteredPage(page, pageSize, lifecycleScope);
  }
}
