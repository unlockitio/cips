package io.unlockit.application.credential.api;

import io.unlockit.application.credential.dto.RegistryInfoResponse;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/v1/registry-info")
@Produces(MediaType.APPLICATION_JSON)
public class RegistryInfoResource {
  private static final String[] ACTIVE = {"active"};
  private static final String[] NO_FILTERS = {};

  @GET
  public RegistryInfoResponse get() {
    var capabilities =
        new RegistryInfoResponse.FamilyCapabilities(
            true, false, false, ACTIVE, NO_FILTERS, 50, 100);
    return new RegistryInfoResponse("1.0.0-draft", capabilities, capabilities);
  }
}
