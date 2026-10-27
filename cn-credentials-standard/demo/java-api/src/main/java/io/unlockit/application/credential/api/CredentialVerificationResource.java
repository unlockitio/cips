package io.unlockit.application.credential.api;

import io.unlockit.application.credential.dto.VerificationResponse;
import io.unlockit.application.credential.mapper.OfflineCredentialVerifier;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.Clock;
import java.util.Set;

@Path("/v1/credential-verifications")
@Produces(MediaType.APPLICATION_JSON)
public class CredentialVerificationResource {
  private static final Set<String> SUPPORTED = Set.of("application/vc+jwt", "application/vc",
      "application/vc+sd-jwt", "application/vc+cose");
  private final OfflineCredentialVerifier verifier;

  public CredentialVerificationResource() { this.verifier = new OfflineCredentialVerifier(Clock.systemUTC()); }

  @POST
  @Consumes({"application/vc+jwt", "application/vc", "application/vc+sd-jwt", "application/vc+cose"})
  public Response verify(byte[] secured, @Context HttpHeaders headers) {
    String mediaType = headers.getMediaType().getType() + "/" + headers.getMediaType().getSubtype();
    if (!SUPPORTED.contains(mediaType) || secured == null || secured.length == 0 || secured.length > 1048576)
      return Response.status(400).entity(java.util.Map.of("error", "Invalid secured credential body")).build();
    VerificationResponse result = verifier.verify(secured, mediaType);
    return Response.ok(result).build();
  }
}
