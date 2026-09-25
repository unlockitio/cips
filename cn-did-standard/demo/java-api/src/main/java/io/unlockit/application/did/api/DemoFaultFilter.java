package io.unlockit.application.did.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import java.time.Duration;
import java.util.Map;
import java.util.Set;

@Provider
public class DemoFaultFilter implements ContainerRequestFilter, ContainerResponseFilter {
    private static final Set<String> FAMILIES = Set.of(
            "did-registries", "dids", "credential-registries", "credentials", "asset-registries", "assets");
    private final boolean enabled;
    private final String mode;
    private final String configuredFamily;
    private final Duration delay;
    private final String instanceId;
    private final ObjectMapper mapper;

    public DemoFaultFilter(
            @ConfigProperty(name = "did.demo.faults.enabled", defaultValue = "false") boolean enabled,
            @ConfigProperty(name = "did.demo.faults.mode", defaultValue = "normal") String mode,
            @ConfigProperty(name = "did.demo.faults.family", defaultValue = "all") String configuredFamily,
            @ConfigProperty(name = "did.demo.faults.delay", defaultValue = "4s") Duration delay,
            @ConfigProperty(name = "did.api.instance-id", defaultValue = "did-api-1") String instanceId,
            ObjectMapper mapper) {
        this.enabled = enabled;
        this.mode = mode;
        this.configuredFamily = configuredFamily;
        this.delay = delay;
        this.instanceId = instanceId;
        this.mapper = mapper;
    }

    @Override
    public void filter(ContainerRequestContext request) {
        String family = family(request.getUriInfo().getPath());
        if (!active(family)) return;
        switch (mode) {
            case "delay" -> delay();
            case "http-500" -> request.abortWith(error(500, "injected_fault", "Demo HTTP 500 fault"));
            case "unavailable" -> request.abortWith(error(503, "injected_unavailable", "Demo unavailable fault"));
            case "normal", "semantic-mismatch", "malformed-json" -> { }
            default -> request.abortWith(error(500, "invalid_fault_configuration", "Unknown demo fault mode"));
        }
    }

    @Override
    public void filter(ContainerRequestContext request, ContainerResponseContext response) {
        String family = family(request.getUriInfo().getPath());
        if (!active(family) || response.getStatus() != 200) return;
        if ("malformed-json".equals(mode)) {
            response.setEntity("{");
            response.getHeaders().putSingle("Content-Type", MediaType.APPLICATION_JSON);
        } else if ("semantic-mismatch".equals(mode)) {
            response.setEntity(mapper.valueToTree(Map.of("fault", "semantic-mismatch", "family", family, "replica", instanceId)));
        }
    }

    private boolean active(String family) {
        return enabled && family != null && ("all".equals(configuredFamily) || family.equals(configuredFamily));
    }

    static String family(String path) {
        String[] segments = (path.startsWith("/") ? path.substring(1) : path).split("/");
        return segments.length >= 2 && "v1".equals(segments[0]) && FAMILIES.contains(segments[1]) ? segments[1] : null;
    }

    private void delay() {
        try {
            java.lang.Thread.sleep(delay.toMillis());
        } catch (InterruptedException exception) {
            java.lang.Thread.currentThread().interrupt();
            throw new IllegalStateException("Demo delay fault interrupted", exception);
        }
    }

    private Response error(int status, String code, String message) {
        return Response.status(status).type(MediaType.APPLICATION_JSON_TYPE)
                .entity(Map.of("status", status, "error", code, "message", message)).build();
    }

}
