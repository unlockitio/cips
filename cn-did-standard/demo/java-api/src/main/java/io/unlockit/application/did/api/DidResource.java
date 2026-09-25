package io.unlockit.application.did.api;

import io.unlockit.application.did.api.representation.DidCollectionRepresentation;
import io.unlockit.application.did.api.representation.DidRepresentation;
import io.unlockit.application.did.manager.DidManager;
import io.unlockit.application.did.mapper.DidRepresentationMapper;
import jakarta.ws.rs.Encoded;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

@Path("/v1/dids")
@Produces(MediaType.APPLICATION_JSON)
public class DidResource {
    private final DidManager manager;

    public DidResource(DidManager manager) { this.manager = manager; }

    @jakarta.inject.Inject io.unlockit.application.did.manager.DidListingManager listings;

    @GET
    public io.unlockit.application.did.manager.DidListingManager.Listing list(
            @jakarta.ws.rs.BeanParam ListingParameters parameters) {
        return parameters.list(listings, "dids", null);
    }

    @GET
    @Path("/capabilities")
    public java.util.Map<String, Object> capabilities() {
        return java.util.Map.of("listings", java.util.Map.of("consistency", "single-pqs-snapshot",
                "states", java.util.List.of("active", "archived", "all"), "pagination", "hmac-nextPageToken",
                "timeFilters", java.util.List.of("createdFrom", "createdUntil", "archivedFrom", "archivedUntil")),
                "bft", java.util.Map.of("singularOnly", true, "collections", false));
    }

    @GET
    @Path("/dso")
    public DidRepresentation dso() {
        return DidRepresentationMapper.toRepresentation(manager.resolveDso());
    }

    @GET
    @Path("/{did}")
    public DidRepresentation resolve(@Encoded @PathParam("did") String encodedDidSegment) {
        return DidRepresentationMapper.toRepresentation(manager.resolve(encodedDidSegment));
    }
}
