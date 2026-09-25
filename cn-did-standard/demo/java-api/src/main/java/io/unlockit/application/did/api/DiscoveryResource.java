package io.unlockit.application.did.api;

import io.smallrye.config.ConfigMapping;
import io.unlockit.application.did.api.representation.DiscoveryRepresentation;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/v1")
@Produces(MediaType.APPLICATION_JSON)
public class DiscoveryResource {
    private final InstanceConfig config;

    public DiscoveryResource(InstanceConfig config) {
        this.config = config;
    }

    @GET
    @Path("/did-registries")
    public DiscoveryRepresentation didRegistries() {
        return descriptor("did-registries");
    }

    @GET
    @Path("/credential-registries")
    public DiscoveryRepresentation credentialRegistries() {
        return descriptor("credential-registries");
    }

    @GET
    @Path("/credentials")
    public DiscoveryRepresentation credentials() {
        return descriptor("credentials");
    }

    @GET
    @Path("/asset-registries")
    public DiscoveryRepresentation assetRegistries() {
        return descriptor("asset-registries");
    }

    @GET
    @Path("/assets")
    public DiscoveryRepresentation assets() {
        return descriptor("assets");
    }

    private DiscoveryRepresentation descriptor(String family) {
        return new DiscoveryRepresentation(family, "v1", config.instanceId(), "discovery-only");
    }

    @ConfigMapping(prefix = "did.api")
    public interface InstanceConfig {
        String instanceId();
    }
}
