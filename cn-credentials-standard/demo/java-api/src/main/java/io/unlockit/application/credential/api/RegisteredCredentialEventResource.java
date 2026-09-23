package io.unlockit.application.credential.api;

import io.unlockit.application.credential.manager.CredentialManager;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/v1/registered-credential-events")
@Produces(MediaType.APPLICATION_JSON)
public class RegisteredCredentialEventResource {
  private final CredentialManager manager;

  @Inject
  public RegisteredCredentialEventResource(CredentialManager manager) {
    this.manager = manager;
  }

  @GET
  public void findPage() {
    manager.rejectHistory();
  }
}
