package io.unlockit.application.bft;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import io.unlockit.application.bft.manager.BftReaderManager;
import io.unlockit.domain.bft.BftReadException;
import io.unlockit.infrastructure.bft.http.ReaderConfig;
import io.unlockit.infrastructure.bft.http.ReplicaHttpClient;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReaderComponentTest {
    @Test
    void realHttpFaultMatrixAndTrustBoundaries() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        var executor = Executors.newVirtualThreadPerTaskExecutor();
        HttpServer[] servers = new HttpServer[4];
        String[] bodies = {"{\"ok\":1}", "{\"ok\":1}", "{\"ok\":1}", "{\"ok\":1}"};
        int[] statuses = {200, 200, 200, 200};
        long[] delays = {0, 0, 0, 0};
        int[] ports = {42003, 42004, 42005, 42006};
        var services = mapper.createArrayNode();
        List<String> families = List.of("did-registries", "dids", "credential-registries", "credentials", "asset-registries", "assets");
        List<String> types = List.of("CantonDidRegistryService", "CantonDidResolutionService", "CantonCredentialRegistryService", "CantonCredentialService", "CantonAssetRegistryService", "CantonAssetService");
        String did = "did:canton:DSO::test";
        for (int f = 0; f < families.size(); f++) {
            var service = services.addObject().put("id", did + "#" + families.get(f)).put("type", types.get(f));
            var endpoints = service.putObject("serviceEndpoint").put("version", "1.0").putArray("endpoints");
            for (int i = 0; i < 4; i++) endpoints.addObject().put("uri", "http://localhost:" + ports[i] + "/v1/" + families.get(f)).put("priority", i);
        }
        var page = mapper.createObjectNode().put("page", 0).put("pageSize", 50).put("hasNext", false);
        var document = page.putArray("items").addObject().put("did", did).putObject("didDocument").put("id", did);
        document.set("service", services);
        document.putArray("controller").add(did);
        document.putArray("verificationMethod");
        var relationships = document.putObject("verificationRelationships");
        for (String field : List.of("authentication", "assertionMethod", "keyAgreement", "capabilityInvocation", "capabilityDelegation")) {
            relationships.putArray(field);
        }
        try {
            for (int i = 0; i < 4; i++) {
                int index = i;
                servers[i] = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
                servers[i].setExecutor(executor);
                servers[i].createContext("/", exchange -> {
                    try {
                        boolean bootstrap = exchange.getRequestURI().getPath().equals("/v1/dids/dso");
                        if (!bootstrap) Thread.sleep(delays[index]);
                        byte[] bytes = (bootstrap ? page.path("items").get(0).toString() : bodies[index]).getBytes(java.nio.charset.StandardCharsets.UTF_8);
                        exchange.getResponseHeaders().set("Content-Type", "application/json");
                        exchange.sendResponseHeaders(bootstrap ? 200 : statuses[index], bytes.length);
                        exchange.getResponseBody().write(bytes);
                    } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
                    finally { exchange.close(); }
                });
                servers[i].start();
            }
            ReaderConfig config = new ReaderConfig() {
                public String registryUrl() { return "http://127.0.0.1:" + servers[0].getAddress().getPort() + "/v1/dids/dso"; }
                public Duration connectTimeout() { return Duration.ofMillis(300); }
                public Duration readTimeout() { return Duration.ofMillis(300); }
                public Duration operationTimeout() { return Duration.ofSeconds(2); }
                public int maxBodyBytes() { return 8192; }
                public String originMap() {
                    return java.util.stream.IntStream.range(0, 4).mapToObj(i -> "localhost:" + ports[i] + "=http://127.0.0.1:" + servers[i].getAddress().getPort()).collect(java.util.stream.Collectors.joining(","));
                }
            };
            var manager = new BftReaderManager(new ReplicaHttpClient(mapper, config), config);
            try {
                for (String family : families) {
                    var healthy = manager.readDid(did, "did%3Acanton%3ADSO%3A%3Atest");
                    assertEquals(3, healthy.quorum().required());
                    assertEquals(4, healthy.quorum().matched());
                    assertEquals(4, healthy.quorum().total());
                    assertEquals("4-of-4", healthy.quorum().agreement());
                    assertEquals(4, healthy.sources().size());
                }
                var fourthEndpoint = (com.fasterxml.jackson.databind.node.ObjectNode) services.get(5).path("serviceEndpoint").path("endpoints").get(3);
                fourthEndpoint.put("uri", "http://localhost:42007/v1/assets");
                assertEquals(502, assertThrows(BftReadException.class, () -> manager.readDid(did, "did%3Acanton%3ADSO%3A%3Atest")).status());
                fourthEndpoint.put("uri", "http://localhost:42006/v1/assets");
                java.util.Arrays.fill(bodies, page.path("items").get(0).toString());
                var resolved = manager.readDid(did, "did%3Acanton%3ADSO%3A%3Atest");
                assertEquals(4, resolved.quorum().matched());
                assertEquals(document, resolved.result().path("didDocument"));
                java.util.Arrays.fill(bodies, "{\"ok\":1}");
                bodies[2] = "{\"ok\":2}";
                assertEquals(3, manager.readDid(did, "did%3Acanton%3ADSO%3A%3Atest").quorum().matched());
                assertEquals("3-of-4", manager.readDid(did, "did%3Acanton%3ADSO%3A%3Atest").quorum().agreement());
                bodies[2] = "{";
                assertEquals(3, manager.readDid(did, "did%3Acanton%3ADSO%3A%3Atest").quorum().matched());
                bodies[2] = "{\"ok\":2}";
                statuses[2] = 500;
                assertEquals("upstream_error", manager.readDid(did, "did%3Acanton%3ADSO%3A%3Atest").sources().get(2).outcome());
                statuses[2] = 200;
                delays[2] = 700;
                assertEquals("timeout", manager.readDid(did, "did%3Acanton%3ADSO%3A%3Atest").sources().get(2).outcome());
                delays[2] = 0;
                bodies[1] = "{\"ok\":2}";
                assertEquals(409, assertThrows(BftReadException.class, () -> manager.readDid(did, "did%3Acanton%3ADSO%3A%3Atest")).status());
                statuses[1] = 503;
                assertEquals(503, assertThrows(BftReadException.class, () -> manager.readDid(did, "did%3Acanton%3ADSO%3A%3Atest")).status());
                statuses[1] = 200;
                bodies[1] = "{";
                bodies[2] = "{\"a\":1,\"a\":2}";
                var failure = assertThrows(BftReadException.class, () -> manager.readDid(did, "did%3Acanton%3ADSO%3A%3Atest"));
                assertEquals(503, failure.status());
                assertEquals(4, failure.sources().size());
                bodies[1] = "{} {}";
                assertEquals(503, assertThrows(BftReadException.class, () -> manager.readDid(did, "did%3Acanton%3ADSO%3A%3Atest")).status());
                statuses[0] = statuses[1] = statuses[3] = 404;
                bodies[0] = bodies[1] = bodies[3] = "{\"status\":404,\"error\":\"did_not_found\",\"message\":\"missing\"}";
                assertEquals(404, assertThrows(BftReadException.class, () -> manager.readDid(did, "did%3Acanton%3ADSO%3A%3Atest")).status());
                // Crash/availability tests, not Byzantine response tests.
                java.util.Arrays.fill(statuses, 200);
                java.util.Arrays.fill(bodies, "{\"ok\":1}");
                assertEquals("4-of-4", manager.readDid(did, "did%3Acanton%3ADSO%3A%3Atest").quorum().agreement());
                servers[3].stop(0);
                assertEquals("3-of-4", manager.readDid(did, "did%3Acanton%3ADSO%3A%3Atest").quorum().agreement());
                servers[2].stop(0);
                assertEquals(503, assertThrows(BftReadException.class, () -> manager.readDid(did, "did%3Acanton%3ADSO%3A%3Atest")).status());
                ((com.fasterxml.jackson.databind.node.ObjectNode) services.get(5).path("serviceEndpoint").path("endpoints").get(0)).put("uri", "http://evil:42003/v1/assets");
                assertEquals(502, assertThrows(BftReadException.class, () -> manager.readDid(did, "did%3Acanton%3ADSO%3A%3Atest")).status());
            } finally { manager.close(); }
        } finally {
            for (HttpServer server : servers) if (server != null) server.stop(0);
            executor.shutdownNow();
        }
    }
}
