package io.unlockit.application.bft.manager;

import com.fasterxml.jackson.databind.JsonNode;
import io.unlockit.application.bft.api.representation.BftReadRepresentation;
import io.unlockit.application.bft.api.representation.BftReadRepresentation.AuthorityRepresentation;
import io.unlockit.application.bft.api.representation.BftReadRepresentation.QuorumRepresentation;
import io.unlockit.application.bft.api.representation.BftReadRepresentation.SourceDiagnosticRepresentation;
import io.unlockit.domain.bft.Authority;
import io.unlockit.domain.bft.BftReadException;
import io.unlockit.domain.bft.SourceObservation;
import io.unlockit.domain.bft.TrustedEndpoint;
import io.unlockit.infrastructure.bft.http.ReplicaHttpClient;
import io.unlockit.infrastructure.bft.http.ReaderConfig;
import jakarta.enterprise.context.ApplicationScoped;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@ApplicationScoped
public class BftReaderManager {
    private static final List<String> FAMILIES = List.of(
            "did-registries", "dids", "credential-registries", "credentials", "asset-registries", "assets");
    private static final Map<String, String> TYPES = Map.of(
            "did-registries", "CantonDidRegistryService",
            "dids", "CantonDidResolutionService",
            "credential-registries", "CantonCredentialRegistryService",
            "credentials", "CantonCredentialService",
            "asset-registries", "CantonAssetRegistryService",
            "assets", "CantonAssetService");
    private final ReplicaHttpClient http;
    private final ReaderConfig config;

    public BftReaderManager(ReplicaHttpClient http, ReaderConfig config) {
        this.http = http;
        this.config = config;
    }

    private final java.util.concurrent.ExecutorService workers = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor();

    @jakarta.annotation.PreDestroy
    public void close() { workers.shutdownNow(); }

    public BftReadRepresentation readDid(String did, String encodedDid) {
        return withinDeadline(() -> {
            Discovery discovery = discover();
            List<TrustedEndpoint> endpoints = discovery.endpoints().get("dids").stream()
                    .map(endpoint -> new TrustedEndpoint(
                            URI.create(endpoint.advertisedUri() + "/" + encodedDid),
                            URI.create(endpoint.internalUri() + "/" + encodedDid), endpoint.priority()))
                    .toList();
            return execute("dids", endpoints, discovery.authority());
        });
    }

    private BftReadRepresentation withinDeadline(java.util.concurrent.Callable<BftReadRepresentation> work) {
        var task = workers.submit(work);
        try {
            return task.get(config.operationTimeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.TimeoutException exception) {
            throw new BftReadException(504, "deadline_exceeded", "Bootstrap and read exceeded the overall deadline");
        } catch (java.util.concurrent.ExecutionException exception) {
            if (exception.getCause() instanceof RuntimeException runtime) throw runtime;
            throw new BftReadException(503, "insufficient_sources", "Read could not complete");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BftReadException(503, "insufficient_sources", "Read was interrupted");
        } finally {
            task.cancel(true);
        }
    }

    private BftReadRepresentation execute(String family, List<TrustedEndpoint> endpoints, Authority authority) {
        List<CompletableFuture<SourceObservation>> futures = endpoints.stream()
                .map(endpoint -> CompletableFuture.supplyAsync(() -> http.fetch(endpoint), workers))
                .toList();
        List<SourceObservation> observations;
        try {
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                    .get(config.operationTimeout().toMillis(), TimeUnit.MILLISECONDS);
            observations = futures.stream().map(CompletableFuture::join).sorted(order()).toList();
        } catch (java.util.concurrent.TimeoutException exception) {
            observations = completedObservations(futures, endpoints);
            futures.forEach(future -> future.cancel(true));
            throw new BftReadException(504, "deadline_exceeded", "BFT read exceeded the overall deadline", observations);
        } catch (Exception exception) {
            if (exception instanceof InterruptedException) java.lang.Thread.currentThread().interrupt();
            throw new BftReadException(503, "insufficient_sources", "Fewer than three sources were comparable");
        }
        Map<String, List<SourceObservation>> votes = new LinkedHashMap<>();
        observations.stream().filter(SourceObservation::votes)
                .forEach(observation -> votes.computeIfAbsent(observation.status() + ":" + observation.digest(), ignored -> new ArrayList<>()).add(observation));
        var winner = votes.values().stream().filter(group -> group.size() >= 3)
                .max(Comparator.<List<SourceObservation>>comparingInt(List::size)
                        .thenComparing(group -> group.getFirst().digest()))
                .orElse(null);
        if (winner == null) {
            long comparable = observations.stream().filter(SourceObservation::votes).count();
            if (comparable == 4) throw new BftReadException(409, "digest_conflict", "Four valid sources have no digest quorum", observations);
            throw new BftReadException(503, "insufficient_sources", "Fewer than three comparable sources agreed", observations);
        }
        SourceObservation representative = winner.stream().min(order()).orElseThrow();
        if (representative.status() == 404) {
            throw new BftReadException(404, "not_found", "Three trusted sources agree that the resource was not found", observations);
        }
        return new BftReadRepresentation(family, representative.value(), representative.digest(),
                new QuorumRepresentation(3, winner.size(), 4, winner.size() == 4 ? "4-of-4" : "3-of-4"),
                observations.stream().map(this::diagnostic).toList(),
                new AuthorityRepresentation(authority.dsoDid(), authority.bootstrapUrl(), authority.advertisedEndpoints()));
    }

    private Discovery discover() {
        JsonNode record = http.bootstrap();
        String did = record.path("did").asText();
        if (!did.matches("did:canton:DSO::[^:/?#\\s]+") || !did.equals(record.path("didDocument").path("id").asText())) {
            throw discoveryError("Bootstrap did not return the local DSO DID");
        }
        JsonNode services = record.path("didDocument").path("service");
        if (!services.isArray() || services.size() != FAMILIES.size()) throw discoveryError("DSO DID must advertise exactly six services");
        Map<String, URI> origins = parseOriginMap();
        Map<String, List<TrustedEndpoint>> endpoints = new LinkedHashMap<>();
        List<String> allAdvertised = new ArrayList<>();
        for (int i = 0; i < FAMILIES.size(); i++) {
            String family = FAMILIES.get(i);
            JsonNode service = services.get(i);
            if (!service.path("id").asText().equals(did + "#" + family)
                    || !service.path("type").asText().equals(TYPES.get(family))
                    || !service.path("serviceEndpoint").path("version").asText().equals("1.0")) {
                throw discoveryError("DSO service structure is not trusted");
            }
            JsonNode advertised = service.path("serviceEndpoint").path("endpoints");
            if (!advertised.isArray() || advertised.size() != 4) throw discoveryError("Each service must advertise exactly four endpoints");
            List<TrustedEndpoint> trusted = new ArrayList<>();
            Set<String> distinct = new java.util.HashSet<>();
            for (int priority = 0; priority < 4; priority++) {
                JsonNode item = advertised.get(priority);
                URI uri;
                try { uri = URI.create(item.path("uri").asText()); } catch (RuntimeException exception) { throw discoveryError("Advertised endpoint is malformed"); }
                if (!item.path("priority").isIntegralNumber() || item.path("priority").asInt(-1) != priority || !"http".equals(uri.getScheme())
                        || uri.getUserInfo() != null || uri.getFragment() != null || uri.getQuery() != null
                        || !uri.getRawPath().equals("/v1/" + family)) throw discoveryError("Advertised endpoint is not trusted");
                String origin = uri.getHost() + ":" + uri.getPort();
                URI internalOrigin = origins.get(origin);
                if (!origin.equals(List.of("localhost:42003", "localhost:42004", "localhost:42005", "localhost:42006").get(priority))
                        || internalOrigin == null || !distinct.add(origin)) throw discoveryError("Advertised origins must be four distinct trusted endpoints");
                URI internal = internalOrigin.resolve(uri.getPath());
                trusted.add(new TrustedEndpoint(uri, internal, priority));
                allAdvertised.add(uri.toString());
            }
            endpoints.put(family, List.copyOf(trusted));
        }
        return new Discovery(Map.copyOf(endpoints), new Authority(did, config.registryUrl(), allAdvertised));
    }

    private Map<String, URI> parseOriginMap() {
        Map<String, URI> result = new LinkedHashMap<>();
        for (String mapping : config.originMap().split(",")) {
            String[] parts = mapping.trim().split("=", 2);
            if (parts.length != 2) throw discoveryError("Internal origin map is malformed");
            URI internal;
            try { internal = URI.create(parts[1]); } catch (RuntimeException exception) { throw discoveryError("Internal origin map is malformed"); }
            if (!parts[0].matches("localhost:4200[3456]") || internal.getHost() == null || internal.getPort() < 1
                    || !"http".equals(internal.getScheme()) || internal.getUserInfo() != null || internal.getFragment() != null
                    || internal.getQuery() != null || !(internal.getPath().isEmpty() || "/".equals(internal.getPath()))) {
                throw discoveryError("Internal origin map is untrusted");
            }
            if (result.put(parts[0], internal) != null) throw discoveryError("Internal origin map contains duplicates");
        }
        if (result.size() != 4 || new java.util.HashSet<>(result.values()).size() != 4) throw discoveryError("Internal origin map must contain exactly four origins");
        return result;
    }

    private List<SourceObservation> completedObservations(
            List<CompletableFuture<SourceObservation>> futures, List<TrustedEndpoint> endpoints) {
        List<SourceObservation> observations = new ArrayList<>();
        for (int index = 0; index < futures.size(); index++) {
            CompletableFuture<SourceObservation> future = futures.get(index);
            if (future.isDone() && !future.isCompletedExceptionally() && !future.isCancelled()) {
                observations.add(future.join());
            } else {
                TrustedEndpoint endpoint = endpoints.get(index);
                observations.add(new SourceObservation(endpoint.advertisedUri().toString(), endpoint.priority(), 0,
                        "timeout", null, null, config.operationTimeout().toMillis()));
            }
        }
        return observations.stream().sorted(order()).toList();
    }

    private Comparator<SourceObservation> order() {
        return Comparator.comparingInt(SourceObservation::priority).thenComparing(SourceObservation::advertisedUri);
    }

    private SourceDiagnosticRepresentation diagnostic(SourceObservation observation) {
        return new SourceDiagnosticRepresentation(observation.advertisedUri(), observation.priority(), observation.status(),
                observation.outcome(), observation.digest(), observation.durationMillis());
    }

    private BftReadException discoveryError(String message) {
        return new BftReadException(502, "untrusted_discovery", message);
    }

    private record Discovery(Map<String, List<TrustedEndpoint>> endpoints, Authority authority) {}
}
