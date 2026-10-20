package io.unlockit.application.did.api;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Set;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Path("/v1")
@Produces(MediaType.APPLICATION_JSON)
public class NameResolutionProxyResource {
  private static final Set<Integer> STATUSES = Set.of(200, 400, 404, 409, 422, 503);
  private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2))
      .followRedirects(HttpClient.Redirect.NEVER).build();
  @ConfigProperty(name = "did.credentials.resolution-url")
  Optional<String> resolutionUrl;

  @GET
  @Path("/resolve")
  public Response resolve(@Context UriInfo uri) {
    return forward("resolve", uri);
  }

  @GET
  @Path("/reverse-resolve")
  public Response reverseResolve(@Context UriInfo uri) {
    return forward("reverse-resolve", uri);
  }

  private Response forward(String route, UriInfo uri) {
    if (resolutionUrl.isEmpty() || resolutionUrl.get().isBlank()) return unavailable();
    String query = uri.getRequestUri().getRawQuery();
    if (query != null && query.length() > 2048) return Response.status(400)
        .entity("{\"error\":\"query too long\"}").build();
    // Never accept a client-selected destination or an unbounded query parameter set.
    String[] parts = query == null || query.isEmpty() ? new String[0] : query.split("&", -1);
    if (parts.length > 3) return Response.status(400)
        .entity("{\"error\":\"too many query parameters\"}").build();
    Set<String> seen = new java.util.HashSet<>();
    for (String part : parts) {
      String key = part.split("=", 2)[0];
      if (!seen.add(key)) return Response.status(400)
          .entity("{\"error\":\"duplicate query parameter\"}").build();
      if (!(key.equals("network") || key.equals("source") ||
          key.equals(route.equals("resolve") ? "name" : "party"))) {
        return Response.status(400).entity("{\"error\":\"unsupported query parameter\"}").build();
      }
    }
    try {
      URI base = URI.create(resolutionUrl.get());
      if (!base.isAbsolute() || !Set.of("http", "https").contains(base.getScheme()) ||
          base.getHost() == null || base.getRawQuery() != null || base.getRawFragment() != null ||
          base.getRawUserInfo() != null || !base.getPath().matches("/?") ||
          base.getHost().equalsIgnoreCase(uri.getRequestUri().getHost()) &&
              base.getPort() == uri.getRequestUri().getPort()) return unavailable();
      URI target = URI.create(resolutionUrl.get().replaceAll("/$", "") + "/v1/" + route +
          (query == null ? "" : "?" + query));
      HttpResponse<String> response = client.send(HttpRequest.newBuilder(target)
          .timeout(Duration.ofSeconds(3)).GET().build(), HttpResponse.BodyHandlers.ofString());
      if (!STATUSES.contains(response.statusCode())) return unavailable();
      String contentType = response.headers().firstValue("content-type").orElse(MediaType.APPLICATION_JSON);
      if (!contentType.toLowerCase(java.util.Locale.ROOT).startsWith(MediaType.APPLICATION_JSON)) return unavailable();
      return Response.status(response.statusCode()).type(contentType).entity(response.body()).build();
    } catch (IOException | InterruptedException | IllegalArgumentException e) {
      if (e instanceof InterruptedException) Thread.currentThread().interrupt();
      return unavailable();
    }
  }

  private Response unavailable() {
    return Response.status(503).entity("{\"error\":\"credentials name resolution unavailable\"}").build();
  }
}
