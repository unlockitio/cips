package io.unlockit.application.credential.api;

import io.unlockit.application.credential.dto.RegisteredCredentialPageResponse;
import io.unlockit.application.credential.dto.RegisteredCredentialResponse;
import io.unlockit.application.credential.manager.CredentialListingManager;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/v1/registered-credentials")
@Produces(MediaType.APPLICATION_JSON)
public class RegisteredCredentialResource {
  private final CredentialListingManager manager;

  @Inject
  public RegisteredCredentialResource(CredentialListingManager manager) { this.manager = manager; }

  @GET
  @Path("/{credentialId}")
  public Response findByCredentialId(@PathParam("credentialId") String credentialId,
      @BeanParam CredentialParameters parameters, @QueryParam("verify") @DefaultValue("false") boolean verify) {
    RegisteredCredentialResponse record = manager.getRegistered(credentialId, parameters.state());
    return verify ? CredentialReadEvaluation.registered(record) : Response.ok(record, MediaType.APPLICATION_JSON).build();
  }

  @GET
  public Response findPage(@BeanParam CredentialParameters parameters,
      @QueryParam("verify") @DefaultValue("false") boolean verify) {
    if (verify) return Response.status(Response.Status.BAD_REQUEST)
        .entity(java.util.Map.of("error", "verify=true is unsupported on collection reads")).build();
    RegisteredCredentialPageResponse page = manager.listRegistered(parameters.page, parameters.pageSize, parameters.nextPageToken, parameters.filters(true));
    return Response.ok(page, MediaType.APPLICATION_JSON).build();
  }
}
