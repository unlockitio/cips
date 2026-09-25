package io.unlockit.infrastructure.bft.http;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class ReplicaHttpClientTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final ReplicaHttpClient client = new ReplicaHttpClient(mapper, new ReaderConfig() {
        public String registryUrl() { return "http://localhost:1/v1/dids"; }
        public Duration connectTimeout() { return Duration.ofMillis(10); }
        public Duration readTimeout() { return Duration.ofMillis(10); }
        public Duration operationTimeout() { return Duration.ofMillis(20); }
        public int maxBodyBytes() { return 1024; }
        public String originMap() { return "localhost:42003=http://a:8080,localhost:42004=http://b:8080,localhost:42005=http://c:8080,localhost:42006=http://d:8080"; }
    });

    @Test
    void canonicalDigestSortsObjectKeysRecursivelyAndPreservesArrayOrderAndTypes() throws Exception {
        var first = mapper.readTree("{\"z\":1,\"nested\":{\"b\":true,\"a\":null},\"items\":[2,\"2\"]}");
        var reordered = mapper.readTree("{\"items\":[2,\"2\"],\"nested\":{\"a\":null,\"b\":true},\"z\":1}");
        var changedArray = mapper.readTree("{\"items\":[\"2\",2],\"nested\":{\"a\":null,\"b\":true},\"z\":1}");
        assertEquals(client.digest(first), client.digest(reordered));
        org.junit.jupiter.api.Assertions.assertNotEquals(client.digest(first), client.digest(changedArray));
    }

    @Test
    void comparisonExcludesReplicaLocalInstanceIdRecursivelyOnly() throws Exception {
        var first = mapper.readTree("{\"family\":\"assets\",\"instanceId\":\"did-api-1\",\"nested\":{\"instanceId\":\"one\",\"id\":1}}");
        var second = mapper.readTree("{\"family\":\"assets\",\"instanceId\":\"did-api-2\",\"nested\":{\"instanceId\":\"two\",\"id\":1}}");
        assertEquals(client.digest(client.withoutInstanceId(first)), client.digest(client.withoutInstanceId(second)));
    }
}
