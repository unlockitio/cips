package io.unlockit.application.bft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
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

/**
 * The seeded DSO document advertises demo-scoped name-resolution services after the
 * six trusted families. Those extra entries must not become voting sources and must
 * not break discovery, while a missing or reordered trusted family still fails.
 */
class ExtraDemoServiceDiscoveryTest {
    private static final List<String> FAMILIES = List.of(
            "did-registries", "dids", "credential-registries", "credentials", "asset-registries", "assets");
    private static final List<String> TYPES = List.of(
            "CantonDidRegistryService", "CantonDidResolutionService", "CantonCredentialRegistryService",
            "CantonCredentialService", "CantonAssetRegistryService", "CantonAssetService");
    private static final int[] PORTS = {42003, 42004, 42005, 42006};

    @Test
    void trailingDemoServicesAreIgnoredAndMissingFamiliesStillFail() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        var executor = Executors.newVirtualThreadPerTaskExecutor();
        HttpServer[] servers = new HttpServer[4];
        ArrayNode services = mapper.createArrayNode();
        String did = "did:canton:DSO::test";
        for (int family = 0; family < FAMILIES.size(); family++) addService(services, did, FAMILIES.get(family), TYPES.get(family));
        addNameResolutionService(services, did, "name-resolution", "CantonDemoNameResolutionService", "resolve");
        addNameResolutionService(services, did, "reverse-name-resolution", "CantonDemoReverseNameResolutionService",
                "reverse-resolve");
        var document = mapper.createObjectNode().put("did", did);
        var didDocument = document.putObject("didDocument").put("id", did);
        didDocument.putArray("controller").add(did);
        didDocument.set("service", services);
        String body = "{\"ok\":1}";
        try {
            for (int index = 0; index < 4; index++) {
                servers[index] = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
                servers[index].setExecutor(executor);
                servers[index].createContext("/", exchange -> {
                    boolean bootstrap = exchange.getRequestURI().getPath().equals("/v1/dids/dso");
                    byte[] bytes = (bootstrap ? document.toString() : body)
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    try {
                        exchange.sendResponseHeaders(200, bytes.length);
                        exchange.getResponseBody().write(bytes);
                    } catch (java.io.IOException ignored) {
                        // The test asserts on the reader result, not on write failures.
                    } finally {
                        exchange.close();
                    }
                });
                servers[index].start();
            }
            HttpServer[] started = servers;
            ReaderConfig config = new ReaderConfig() {
                public String registryUrl() { return "http://127.0.0.1:" + started[0].getAddress().getPort() + "/v1/dids/dso"; }
                public Duration connectTimeout() { return Duration.ofMillis(300); }
                public Duration readTimeout() { return Duration.ofMillis(300); }
                public Duration operationTimeout() { return Duration.ofSeconds(2); }
                public int maxBodyBytes() { return 8192; }
                public String originMap() {
                    return java.util.stream.IntStream.range(0, 4)
                            .mapToObj(index -> "localhost:" + PORTS[index] + "=http://127.0.0.1:"
                                    + started[index].getAddress().getPort())
                            .collect(java.util.stream.Collectors.joining(","));
                }
            };
            var manager = new BftReaderManager(new ReplicaHttpClient(mapper, config), config);
            try {
                var resolved = manager.readDid(did, "did%3Acanton%3ADSO%3A%3Atest");
                assertEquals("4-of-4", resolved.quorum().agreement());
                assertEquals(4, resolved.sources().size());
                for (var source : resolved.sources()) {
                    assertEquals("http://localhost:" + PORTS[source.priority()]
                                    + "/v1/dids/did%3Acanton%3ADSO%3A%3Atest",
                            source.advertisedUri(),
                            "demo name-resolution endpoints must never become voting sources");
                }
                services.remove(0);
                assertEquals(502, assertThrows(BftReadException.class,
                        () -> manager.readDid(did, "did%3Acanton%3ADSO%3A%3Atest")).status());
            } finally { manager.close(); }
        } finally {
            for (HttpServer server : servers) if (server != null) server.stop(0);
            executor.shutdownNow();
        }
    }

    private static void addService(ArrayNode services, String did, String family, String type) {
        var service = services.addObject().put("id", did + "#" + family).put("type", type);
        var endpoints = service.putObject("serviceEndpoint").put("version", "1.0").putArray("endpoints");
        for (int index = 0; index < 4; index++) {
            endpoints.addObject().put("uri", "http://localhost:" + PORTS[index] + "/v1/" + family)
                    .put("priority", index);
        }
    }

    private static void addNameResolutionService(ArrayNode services, String did, String fragment, String type,
            String path) {
        var service = services.addObject().put("id", did + "#" + fragment).put("type", type);
        service.putObject("serviceEndpoint").put("version", "1.0").putArray("endpoints").addObject()
                .put("uri", "http://localhost:41003/v1/" + path).put("priority", 0);
    }
}
