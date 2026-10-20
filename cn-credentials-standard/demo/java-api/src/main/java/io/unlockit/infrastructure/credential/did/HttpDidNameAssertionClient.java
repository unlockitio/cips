package io.unlockit.infrastructure.credential.did;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.unlockit.domain.credential.did.DidNameAssertionClient;
import io.unlockit.domain.credential.exception.DidUnavailableException;
import io.unlockit.domain.credential.exception.UnsupportedCapabilityException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Narrow adapter over the DID demo's bounded alias lookup. It is configured with
 * a single base URL and returns self-asserted hints only. The adapter never
 * treats an assertion as a name allocation.
 */
@ApplicationScoped
public class HttpDidNameAssertionClient implements DidNameAssertionClient {
  private final Optional<String> baseUrl;
  private final HttpClient http;

  @Inject
  public HttpDidNameAssertionClient(
      @ConfigProperty(name = "credentials.did.assertions-url") Optional<String> baseUrl) {
    this(baseUrl, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build());
  }

  HttpDidNameAssertionClient(Optional<String> baseUrl, HttpClient http) {
    this.baseUrl = baseUrl.filter(value -> !value.isBlank());
    this.http = http;
  }

  @Override
  public boolean enabled() {
    return baseUrl.isPresent();
  }

  @Override
  public List<DidNameAssertion> findByAlias(String network, String name) {
    return query("alias", "urn:fqpn:" + network + ":demo:" + name);
  }

  @Override
  public List<DidNameAssertion> findBySubject(String party) {
    return query("subject", party);
  }

  private List<DidNameAssertion> query(String parameter, String value) {
    String root = baseUrl.orElseThrow(() -> new UnsupportedCapabilityException(
        "DID name resolution is not configured for this deployment"));
    URI uri = URI.create(root + "?" + parameter + "="
        + java.net.URLEncoder.encode(value, StandardCharsets.UTF_8));
    try {
      HttpResponse<String> response = http.send(
          HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(2)).GET().build(),
          HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() != 200)
        throw new DidUnavailableException("DID assertion lookup returned " + response.statusCode(), null);
      JsonNode body = new ObjectMapper().readTree(response.body());
      if (!body.isArray()) throw new DidUnavailableException("DID assertion lookup returned an unexpected body", null);
      var assertions = new ArrayList<DidNameAssertion>();
      for (JsonNode item : body) {
        assertions.add(new DidNameAssertion(item.path("did").asText(null),
            item.path("alias").asText(null), item.path("subject").asText(null)));
      }
      return assertions;
    } catch (java.io.IOException | IllegalArgumentException failure) {
      throw new DidUnavailableException("DID assertion lookup failed", failure);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new DidUnavailableException("DID assertion lookup was interrupted", interrupted);
    }
  }
}
