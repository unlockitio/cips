package io.unlockit.credential.bft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import jakarta.ws.rs.WebApplicationException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class CredentialBftReaderTest {
  private static final String RECORD = """
      {"contractId":"cid","credentialId":"urn:demo:test","lifecycleState":"active", "credential":{
      "@context":["https://www.w3.org/ns/credentials/v2"],"id":"urn:demo:test", "type":["VerifiableCredential"],
      "issuer":"issuer","credentialSubject":[{"id":"holder"}]},"canton":{"holders":[],"anchorers":["issuer"]},"lifecycle":{"state":"active"}}
      """;
  private static final String ABSENT = "{\"error\":\"credential not found\"}";

  @Test
  void consumerQuorumAndFailClosedFaultMatrix() throws Exception {
    HttpServer[] servers = new HttpServer[4];
    int[] statuses = {200, 200, 200, 200};
    String[] bodies = {RECORD, RECORD, RECORD, RECORD};
    try {
      StringBuilder origins = new StringBuilder();
      for (int i = 0; i < 4; i++) {
        servers[i] = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        final int index = i;
        servers[i].createContext("/v1/credentials/", exchange -> {
          byte[] content = bodies[index].getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(statuses[index], content.length);
          try (var output = exchange.getResponseBody()) { output.write(content); }
        });
        servers[i].start();
        if (i > 0) origins.append(',');
        origins.append("http://127.0.0.1:").append(servers[i].getAddress().getPort());
      }
      String configured = origins.toString();
      CredentialBftReader reader = new CredentialBftReader(new CredentialBftReader.Settings() {
        public String origins() { return configured; }
        public Duration connectTimeout() { return Duration.ofMillis(200); }
        public Duration readTimeout() { return Duration.ofMillis(200); }
        public Duration operationTimeout() { return Duration.ofSeconds(2); }
        public int maxBodyBytes() { return 4096; }
      }, new ObjectMapper());
      assertEquals("4-of-4", reader.read("urn%3Ademo%3Atest").quorum().agreement());
      var enveloped = new ObjectMapper().readTree(RECORD).deepCopy();
      var envelope = new ObjectMapper().createObjectNode();
      envelope.set("@context", new ObjectMapper().createObjectNode().put("id", "@id")
          .put("type", "@type").put("EnvelopedVerifiableCredential", "https://www.w3.org/2018/credentials#EnvelopedVerifiableCredential"));
      envelope.put("id", "data:application/vc+jwt,header.payload.signature");
      envelope.put("type", "EnvelopedVerifiableCredential");
      ((com.fasterxml.jackson.databind.node.ObjectNode) enveloped).set("credential", envelope);

      java.util.Arrays.fill(bodies, enveloped.toString());
      assertEquals("4-of-4", reader.read("urn%3Ademo%3Atest").quorum().agreement());
      assertEquals(false, reader.read("urn%3Ademo%3Atest").result().has("verification"));
      bodies[3] = enveloped.deepCopy().toString().replace("\"canton\"", "\"verification\":{\"status\":\"VERIFIED\"},\"canton\"");
      assertEquals(3, reader.read("urn%3Ademo%3Atest").quorum().matched());
      java.util.Arrays.fill(bodies, RECORD);
      bodies[3] = "{";
      assertEquals("3-of-4", reader.read("urn%3Ademo%3Atest").quorum().agreement());
      bodies[2] = "{\"contractId\":\"cid\"}";
      assertEquals(503, status(() -> reader.read("urn%3Ademo%3Atest")));
      bodies[2] = RECORD;
      bodies[3] = RECORD.replace("\"credentialId\":\"urn:demo:test\"", "\"credentialId\":\"other\"");
      assertEquals(3, reader.read("urn%3Ademo%3Atest").quorum().matched());
      bodies[3] = RECORD.replace("\"cid\"", "\"different\"");
      assertEquals(3, reader.read("urn%3Ademo%3Atest").quorum().matched());
      bodies[2] = RECORD.replace("\"cid\"", "\"other\"");
      assertEquals(409, status(() -> reader.read("urn%3Ademo%3Atest")));
      bodies[2] = RECORD.replace("\"cid\"", "\"other\"").replace("\"credentialSubject\"", "\"missingSubject\"");
      assertEquals(503, status(() -> reader.read("urn%3Ademo%3Atest")));
      bodies[0] = bodies[1] = bodies[2] = ABSENT;
      statuses[0] = statuses[1] = statuses[2] = 404;
      assertEquals(404, status(() -> reader.read("urn%3Ademo%3Atest")));
      bodies[2] = "{\"error\":\"other missing\"}";
      assertEquals(503, status(() -> reader.read("urn%3Ademo%3Atest")));
      statuses[0] = statuses[1] = statuses[2] = 200;
      bodies[0] = bodies[1] = RECORD;
      bodies[2] = RECORD.replace("\"cid\"", "\"cid\",\"contractId\":\"cid\"");
      bodies[3] = RECORD;
      assertEquals(3, reader.read("urn%3Ademo%3Atest").quorum().matched());
      statuses[3] = 503;
      assertEquals(503, status(() -> reader.read("urn%3Ademo%3Atest")));
      assertEquals(400, status(() -> reader.read("../wrong")));
    } finally {
      for (HttpServer server : servers) if (server != null) server.stop(0);
    }
  }

  private static int status(Runnable action) {
    return assertThrows(WebApplicationException.class, action::run).getResponse().getStatus();
  }
}
