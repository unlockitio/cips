package io.unlockit.infrastructure.bft.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.unlockit.domain.bft.BftReadException;
import io.unlockit.domain.bft.SourceObservation;
import io.unlockit.domain.bft.TrustedEndpoint;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

@ApplicationScoped
public class ReplicaHttpClient {
    private final ObjectMapper mapper;
    private final ReaderConfig config;

    public ReplicaHttpClient(ObjectMapper mapper, ReaderConfig config) {
        this.mapper = mapper.copy()
                .enable(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .enable(com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
        this.config = config;
        if (config.maxBodyBytes() < 1 || config.maxBodyBytes() > 1048576 * 16
                || config.connectTimeout().toMillis() < 1 || config.readTimeout().toMillis() < 1
                || config.operationTimeout().toMillis() < 1) {
            throw new IllegalArgumentException("Invalid reader limits");
        }
    }

    public JsonNode bootstrap() {
        HttpURLConnection connection = null;
        try {
            URI uri = URI.create(config.registryUrl());
            if (!"http".equals(uri.getScheme()) || uri.getHost() == null || uri.getPort() < 1
                    || uri.getUserInfo() != null || uri.getFragment() != null
                    || !"/v1/dids/dso".equals(uri.getRawPath()) || uri.getQuery() != null) {
                throw new IOException("Untrusted registry URL");
            }
            connection = connect(uri);
            if (connection.getResponseCode() != 200 || !isJson(connection)) throw new IOException("Invalid bootstrap response");
            return parseBounded(connection.getInputStream());
        } catch (Exception exception) {
            throw new BftReadException(502, "bootstrap_failed", "Trusted bootstrap registry is unavailable or invalid");
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    public SourceObservation fetch(TrustedEndpoint endpoint) {
        long started = System.nanoTime();
        int status = 0;
        HttpURLConnection connection = null;
        try {
            connection = connect(endpoint.internalUri());
            status = connection.getResponseCode();
            if (status != 200 && status != 404) {
                return observation(endpoint, status, status >= 500 ? "upstream_error" : "non_comparable", null, null, started);
            }
            if (!isJson(connection)) return observation(endpoint, status, "non_comparable", null, null, started);
            JsonNode value = parseBounded(status == 404 ? connection.getErrorStream() : connection.getInputStream());
            if (status == 404 && (!value.isObject() || !value.path("status").isIntegralNumber()
                    || value.path("status").intValue() != 404 || !"did_not_found".equals(value.path("error").asText()))) {
                return observation(endpoint, status, "non_comparable", null, null, started);
            }
            return observation(endpoint, status, status == 404 ? "not_found" : "valid",
                    digest(withoutInstanceId(value)), value, started);
        } catch (SocketTimeoutException exception) {
            return observation(endpoint, status, "timeout", null, null, started);
        } catch (com.fasterxml.jackson.core.JacksonException exception) {
            return observation(endpoint, status, "invalid_json", null, null, started);
        } catch (IOException exception) {
            return observation(endpoint, status, status == 0 ? "unavailable" : "non_comparable", null, null, started);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private HttpURLConnection connect(URI uri) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection(java.net.Proxy.NO_PROXY);
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(Math.toIntExact(config.connectTimeout().toMillis()));
        connection.setReadTimeout(Math.toIntExact(config.readTimeout().toMillis()));
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Connection", "close");
        return connection;
    }

    private boolean isJson(HttpURLConnection connection) {
        String type = connection.getContentType();
        return type != null && "application/json".equals(type.split(";", 2)[0].trim().toLowerCase(Locale.ROOT));
    }

    private JsonNode parseBounded(InputStream input) throws IOException {
        if (input == null) throw new IOException("Missing body");
        long deadline = System.nanoTime() + config.readTimeout().toNanos();
        try (input; ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int total = 0;
            for (int read; (read = input.read(buffer)) != -1;) {
                if (Thread.currentThread().isInterrupted() || System.nanoTime() >= deadline) throw new SocketTimeoutException();
                total += read;
                if (total > config.maxBodyBytes()) throw new IOException("Response body limit exceeded");
                bytes.write(buffer, 0, read);
            }
            JsonNode result = mapper.readTree(bytes.toByteArray());
            if (result == null || result.isMissingNode()) throw new IOException("Empty JSON body");
            return result;
        }
    }

    public JsonNode withoutInstanceId(JsonNode value) {
        JsonNode copy = value.deepCopy();
        removeInstanceId(copy);
        return copy;
    }

    private void removeInstanceId(JsonNode value) {
        if (value.isObject()) {
            ((ObjectNode) value).remove("instanceId");
            value.elements().forEachRemaining(this::removeInstanceId);
        } else if (value.isArray()) {
            value.elements().forEachRemaining(this::removeInstanceId);
        }
    }

    public String digest(JsonNode value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(canonicalize(value))));
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot canonicalize JSON", exception);
        }
    }

    private JsonNode canonicalize(JsonNode value) {
        if (value.isObject()) {
            ObjectNode sorted = mapper.createObjectNode();
            List<String> names = new ArrayList<>();
            value.fieldNames().forEachRemaining(names::add);
            names.sort(Comparator.naturalOrder());
            names.forEach(name -> sorted.set(name, canonicalize(value.get(name))));
            return sorted;
        }
        if (value.isArray()) {
            ArrayNode ordered = mapper.createArrayNode();
            value.forEach(item -> ordered.add(canonicalize(item)));
            return ordered;
        }
        return value;
    }

    private SourceObservation observation(TrustedEndpoint endpoint, int status, String outcome,
            String digest, JsonNode value, long started) {
        return new SourceObservation(endpoint.advertisedUri().toString(), endpoint.priority(), status,
                outcome, digest, value, Duration.ofNanos(System.nanoTime() - started).toMillis());
    }
}
