package io.unlockit.application.did.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.sun.net.httpserver.HttpServer;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestProfile(NameResolutionProxyResourceTest.Profile.class)
class NameResolutionProxyResourceTest {
  private static final HttpServer upstream = startServer();
  private static final AtomicReference<String> REQUEST = new AtomicReference<>();
  private static volatile int upstreamStatus = 200;

  public static class Profile implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
      return Map.of("did.credentials.resolution-url", "http://localhost:" + upstream.getAddress().getPort());
    }
  }

  static HttpServer startServer() {
    try {
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/", exchange -> {
      REQUEST.set(exchange.getRequestURI().toString());
      byte[] body = (exchange.getRequestURI().getPath().equals("/v1/reverse-resolve") ? "[]" : "{\"fqpn\":\"localnet:demo:alice.canton\"}")
          .getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.sendResponseHeaders(upstreamStatus, body.length);
      try (var out = exchange.getResponseBody()) { out.write(body); }
    });
    server.start();
    return server;
    } catch (IOException e) { throw new IllegalStateException(e); }
  }

  @AfterAll
  static void stop() { upstream.stop(0); }

  @Test
  void forwardsBothRoutesAndEncodedQueryWithoutChangingAuthority() {
    upstreamStatus = 200;
    given().urlEncodingEnabled(false).get("/v1/resolve?name=alice%2Ecanton&network=localnet&source=did")
        .then().statusCode(200).contentType("application/json").body("fqpn", equalTo("localnet:demo:alice.canton"));
    assertEquals("/v1/resolve?name=alice%2Ecanton&network=localnet&source=did", REQUEST.get());
    given().urlEncodingEnabled(false).get("/v1/reverse-resolve?party=Alice%3A%3A123&network=localnet&source=did")
        .then().statusCode(200).contentType("application/json").body("size()", equalTo(0));
    assertEquals("/v1/reverse-resolve?party=Alice%3A%3A123&network=localnet&source=did", REQUEST.get());
  }

  @Test
  void preservesBackendErrorsAndFailsClosed() {
    for (int status : new int[] {400, 404, 409, 422, 503}) {
      upstreamStatus = status;
      given().get("/v1/resolve?name=x&network=localnet").then().statusCode(status);
    }
    given().get("/v1/resolve?forward-to-party=localhost&network=localnet").then().statusCode(400);
    given().get("/v1/resolve?name=x&name=y&network=localnet").then().statusCode(400);
    upstreamStatus = 500;
    given().get("/v1/reverse-resolve?party=x&network=localnet").then().statusCode(503);
    upstreamStatus = 503;
    given().get("/v1/reverse-resolve?party=x&network=localnet").then().statusCode(503);
  }
}
