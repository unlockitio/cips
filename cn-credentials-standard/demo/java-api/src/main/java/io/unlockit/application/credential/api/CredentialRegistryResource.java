package io.unlockit.application.credential.api;

import io.unlockit.application.credential.dto.CredentialRegistryPageResponse;
import io.unlockit.application.credential.dto.CredentialRegistryResponse;
import io.unlockit.application.credential.manager.CredentialRegistryManager;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

@Path("/v1/credential-registries")
@Produces(MediaType.APPLICATION_JSON)
public class CredentialRegistryResource {
  private final CredentialRegistryManager manager;

  @Inject
  public CredentialRegistryResource(CredentialRegistryManager manager) {
    this.manager = manager;
  }

  @GET
  public CredentialRegistryPageResponse findPage(
      @QueryParam("page") String page, @QueryParam("pageSize") String pageSize) {
    return manager.findPage(page, pageSize);
  }

  @GET
  @Path("/{registryId}")
  public CredentialRegistryResponse findByRegistryId(@PathParam("registryId") String registryId) {
    return manager.findByRegistryId(registryId);
  }
}
