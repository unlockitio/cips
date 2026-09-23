package io.unlockit.application.credential.api;

import io.unlockit.application.credential.dto.CredentialPageResponse;
import io.unlockit.application.credential.dto.CredentialResponse;
import io.unlockit.application.credential.manager.CredentialManager;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

@Path("/v1/credentials")
@Produces(MediaType.APPLICATION_JSON)
public class CredentialResource {
  private final CredentialManager manager;

  @Inject
  public CredentialResource(CredentialManager manager) {
    this.manager = manager;
  }

  @GET
  @Path("/{credentialId}")
  public CredentialResponse findByCredentialId(
      @PathParam("credentialId") String credentialId,
      @QueryParam("lifecycleScope") String lifecycleScope) {
    return manager.findByCredentialId(credentialId, lifecycleScope);
  }

  @GET
  public CredentialPageResponse findPage(
      @QueryParam("page") String page,
      @QueryParam("pageSize") String pageSize,
      @QueryParam("lifecycleScope") String lifecycleScope) {
    return manager.findPage(page, pageSize, lifecycleScope);
  }
}
