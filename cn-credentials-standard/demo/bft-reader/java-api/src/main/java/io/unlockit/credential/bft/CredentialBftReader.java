package io.unlockit.credential.bft;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.smallrye.config.ConfigMapping;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.annotation.PreDestroy;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Path("/v1/bft/credentials")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class CredentialBftReader {
  @ConfigMapping(prefix = "credential.bft")
  public interface Settings {
    String origins();
    Duration connectTimeout();
    Duration readTimeout();
    Duration operationTimeout();
    int maxBodyBytes();
  }

  public record Source(int priority, int status, String outcome, String digest) {}
  public record Quorum(int required, int matched, int total, String agreement) {}
  public record Result(JsonNode result, String digest, Quorum quorum, List<Source> sources) {}
  private record Vote(Source source, JsonNode body) {}

  private final Settings settings;
  private final ObjectMapper mapper;
  private final List<URI> origins;
  private final ExecutorService workers = Executors.newFixedThreadPool(5);

  @Inject
  public CredentialBftReader(Settings settings, ObjectMapper mapper) {
    this.settings = settings;
    this.mapper = mapper.copy().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
        .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
    if (settings.maxBodyBytes() < 1 || settings.maxBodyBytes() > 1048576
        || settings.connectTimeout().isNegative() || settings.connectTimeout().isZero()
        || settings.readTimeout().isNegative() || settings.readTimeout().isZero()
        || settings.operationTimeout().isNegative() || settings.operationTimeout().isZero())
      throw new IllegalArgumentException("Invalid reader limits");
    String[] configured = settings.origins().split(",", -1);
    if (configured.length != 4 || Arrays.stream(configured).distinct().count() != 4)
      throw new IllegalArgumentException("Four distinct credential origins are required");
    this.origins = Arrays.stream(configured).map(URI::create).toList();
    for (URI origin : origins) {
      if (!"http".equals(origin.getScheme()) || origin.getHost() == null || origin.getPort() < 1
          || origin.getUserInfo() != null || origin.getQuery() != null || origin.getFragment() != null
          || !origin.getRawPath().isEmpty()) throw new IllegalArgumentException("Invalid credential origin");
    }
  }

  @PreDestroy
  void shutdown() { workers.shutdownNow(); }

  @GET
  @Path("/{credentialId}")
  public Result read(@jakarta.ws.rs.Encoded @PathParam("credentialId") String encodedId) {
    String credentialId;
    try {
      if (encodedId == null || encodedId.isBlank() || encodedId.contains("/") || encodedId.contains("+"))
        throw new IllegalArgumentException();
      byte[] bytes = new byte[encodedId.length()];
      int count = 0;
      for (int i = 0; i < encodedId.length(); i++) {
        char ch = encodedId.charAt(i);
        if (ch == '%') {
          if (i + 2 >= encodedId.length()) throw new IllegalArgumentException();
          bytes[count++] = (byte) Integer.parseInt(encodedId.substring(i + 1, i + 3), 16);
          i += 2;
        } else {
          if (ch > 127) throw new IllegalArgumentException();
          bytes[count++] = (byte) ch;
        }
      }
      credentialId = StandardCharsets.UTF_8.newDecoder()
          .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
          .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
          .decode(java.nio.ByteBuffer.wrap(bytes, 0, count)).toString();
      if (credentialId.isBlank() || credentialId.contains("/") || credentialId.contains("%")
          || credentialId.contains("?") || credentialId.contains("#") || credentialId.contains("\\")
          || credentialId.chars().anyMatch(Character::isISOControl))
        throw new IllegalArgumentException();
    } catch (Exception invalid) {
      throw error(400, "invalid_request");
    }
    String segment = java.net.URLEncoder.encode(credentialId, StandardCharsets.UTF_8).replace("+", "%20");
    CompletableFuture<Vote>[] tasks = new CompletableFuture[4];
    final String requestedId = credentialId;
    for (int i = 0; i < 4; i++) {
      final int priority = i;
      tasks[i] = CompletableFuture.supplyAsync(() -> fetch(priority, segment, requestedId), workers);
    }
    Vote[] votes = new Vote[4];
    long deadline = System.nanoTime() + settings.operationTimeout().toNanos();
    boolean timedOut = false;
    try {
      for (int i = 0; i < 4; i++) {
        long left = deadline - System.nanoTime();
        if (left <= 0) { timedOut = true; break; }
        try { votes[i] = tasks[i].get(left, TimeUnit.NANOSECONDS); }
        catch (java.util.concurrent.TimeoutException ex) { timedOut = true; break; }
      }
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw error(503, "insufficient_sources");
    } catch (java.util.concurrent.ExecutionException ex) {
      throw error(503, "insufficient_sources");
    } finally {
      for (CompletableFuture<Vote> task : tasks) task.cancel(true);
    }
    if (timedOut) throw error(504, "deadline_exceeded");
    Map<String, List<Vote>> groups = new LinkedHashMap<>();
    for (Vote vote : votes) {
      if (vote.source.digest() != null)
        groups.computeIfAbsent(vote.source.status() + ":" + vote.source.digest(), ignored -> new ArrayList<>()).add(vote);
    }
    List<Vote> winner = groups.values().stream().filter(group -> group.size() >= 3)
        .max(Comparator.comparingInt(List::size)).orElse(null);
    if (winner == null) {
      int comparable = (int) Arrays.stream(votes).filter(v -> v.source.digest() != null).count();
      throw error(comparable == 4 ? 409 : 503, comparable == 4 ? "digest_conflict" : "insufficient_sources");
    }
    Vote representative = winner.getFirst();
    if (representative.source.status() == 404) throw error(404, "not_found");
    return new Result(representative.body, representative.source.digest(),
        new Quorum(3, winner.size(), 4, winner.size() == 4 ? "4-of-4" : "3-of-4"),
        Arrays.stream(votes).map(Vote::source).toList());
  }

  private Vote fetch(int index, String segment, String expectedId) {
    HttpURLConnection connection = null;
    int status = 0;
    try {
      URI uri = URI.create(origins.get(index) + "/v1/credentials/" + segment);
      connection = (HttpURLConnection) uri.toURL().openConnection(java.net.Proxy.NO_PROXY);
      connection.setInstanceFollowRedirects(false);
      connection.setConnectTimeout(Math.toIntExact(settings.connectTimeout().toMillis()));
      connection.setReadTimeout(Math.toIntExact(settings.readTimeout().toMillis()));
      connection.setRequestProperty("Accept", "application/json");
      status = connection.getResponseCode();
      if (status != 200 && status != 404)
        return invalid(index, status, status >= 500 ? "upstream_error" : "non_comparable");
      String type = connection.getContentType();
      if (type == null || !"application/json".equals(type.split(";", 2)[0].trim().toLowerCase(Locale.ROOT)))
        return invalid(index, status, "non_comparable");
      JsonNode body = bounded(status == 404 ? connection.getErrorStream() : connection.getInputStream());
      if (status == 404) {
        if (!body.isObject() || body.size() != 1 || !body.path("error").isTextual()
            || !"credential not found".equals(body.path("error").textValue()))
          return invalid(index, status, "non_comparable");
        return new Vote(new Source(index, 404, "not_found", digest(body)), body);
      }
      if (!valid(body, expectedId)) return invalid(index, status, "non_comparable");
      return new Vote(new Source(index, 200, "valid", digest(body)), body);
    } catch (SocketTimeoutException exception) {
      return invalid(index, status, "timeout");
    } catch (Exception exception) {
      return invalid(index, status, "unavailable");
    } finally {
      if (connection != null) connection.disconnect();
    }
  }

  private boolean valid(JsonNode body, String id) {
    if (!body.isObject() || !body.path("contractId").isTextual() || body.path("contractId").asText().isBlank()
        || !body.path("credentialId").isTextual() || !id.equals(body.path("credentialId").asText())
        || !body.path("lifecycleState").isTextual() || !List.of("active", "archived").contains(body.path("lifecycleState").asText())) return false;
    JsonNode credential = body.path("credential");
    if (body.has("verification") || body.has("schemaValidation") || !credential.isObject()) return false;
    boolean enveloped = "EnvelopedVerifiableCredential".equals(credential.path("type").asText());
    if (enveloped) {
      if (!credential.path("@context").isObject() || !credential.path("id").asText().startsWith("data:application/vc+")) return false;
    } else {
      if (!credential.path("id").isTextual() || !id.equals(credential.path("id").asText())
          || !credential.path("issuer").isTextual() || credential.path("issuer").asText().isBlank()
          || !credential.path("@context").isArray() || credential.path("@context").isEmpty()
          || !credential.path("@context").get(0).isTextual()
          || !"https://www.w3.org/ns/credentials/v2".equals(credential.path("@context").get(0).asText())
          || !credential.path("type").isArray() || credential.path("type").isEmpty()
          || !credential.path("credentialSubject").isArray() || credential.path("credentialSubject").isEmpty()) return false;
      boolean vc = false;
      for (JsonNode type : credential.path("type")) {
        if (!type.isTextual() || type.asText().isBlank()) return false;
        if ("VerifiableCredential".equals(type.asText())) vc = true;
      }
      if (!vc) return false;
      for (JsonNode subject : credential.path("credentialSubject")) if (!subject.isObject()) return false;
    }
    JsonNode canton = body.path("canton");
    if (!canton.isObject() || !canton.path("holders").isArray() || !canton.path("anchorers").isArray()
        || canton.path("anchorers").isEmpty()) return false;
    for (JsonNode party : canton.path("holders")) if (!party.isTextual() || party.asText().isBlank()) return false;
    for (JsonNode party : canton.path("anchorers")) if (!party.isTextual() || party.asText().isBlank()) return false;
    if (!body.path("lifecycle").isObject()
        || !body.path("lifecycle").path("state").isTextual()
        || !body.path("lifecycleState").asText().equals(body.path("lifecycle").path("state").asText())) return false;
    return true;
  }

  private JsonNode bounded(InputStream input) throws IOException {
    if (input == null) throw new IOException("Missing body");
    try (input; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      byte[] buffer = new byte[8192];
      long deadline = System.nanoTime() + settings.readTimeout().toNanos();
      int count;
      while ((count = input.read(buffer)) != -1) {
        if (Thread.currentThread().isInterrupted() || System.nanoTime() >= deadline) throw new SocketTimeoutException();
        if (out.size() + count > settings.maxBodyBytes()) throw new IOException("Body exceeds limit");
        out.write(buffer, 0, count);
      }
      JsonNode body = mapper.readTree(out.toByteArray());
      if (body == null) throw new IOException("Empty body");
      return body;
    }
  }

  private String digest(JsonNode body) throws Exception {
    return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(canonical(body))));
  }

  private JsonNode canonical(JsonNode value) {
    if (value.isObject()) {
      ObjectNode sorted = mapper.createObjectNode();
      List<String> names = new ArrayList<>();
      value.fieldNames().forEachRemaining(names::add);
      names.stream().sorted().forEach(name -> sorted.set(name, canonical(value.get(name))));
      return sorted;
    }
    if (value.isArray()) {
      ArrayNode array = mapper.createArrayNode();
      value.forEach(item -> array.add(canonical(item)));
      return array;
    }
    return value;
  }

  private Vote invalid(int index, int status, String outcome) {
    return new Vote(new Source(index, status, outcome, null), null);
  }

  private WebApplicationException error(int status, String code) {
    return new WebApplicationException(Response.status(status).entity(Map.of("status", status, "error", code)).build());
  }
}
