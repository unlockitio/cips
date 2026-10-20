package io.unlockit.application.credential.api;

import io.unlockit.application.credential.dto.ResolutionResult;
import io.unlockit.application.credential.manager.NameResolutionManager;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.util.List;

@Path("/v1")
@Produces(MediaType.APPLICATION_JSON)
public class NameResolutionResource {
  private final NameResolutionManager manager;

  @Inject
  public NameResolutionResource(NameResolutionManager manager) { this.manager = manager; }

  @GET
  @Path("/resolve")
  public ResolutionResult resolve(@QueryParam("name") String name,
      @QueryParam("network") String network, @QueryParam("source") String source) {
    return manager.resolve(name, network, source);
  }

  @GET
  @Path("/reverse-resolve")
  public List<ResolutionResult> reverseResolve(@QueryParam("party") String party,
      @QueryParam("network") String network, @QueryParam("source") String source) {
    return manager.reverseResolve(party, network, source);
  }
}
