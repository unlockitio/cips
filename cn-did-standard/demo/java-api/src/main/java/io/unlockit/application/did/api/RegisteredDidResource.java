package io.unlockit.application.did.api;

import io.unlockit.application.did.api.representation.RegisteredDidCollectionRepresentation;
import io.unlockit.application.did.api.representation.RegisteredDidRepresentation;
import io.unlockit.application.did.manager.DidManager;
import io.unlockit.application.did.mapper.DidRepresentationMapper;
import jakarta.ws.rs.Encoded;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

@Path("/v1/registered-dids")
@Produces(MediaType.APPLICATION_JSON)
public class RegisteredDidResource {
    private final DidManager manager;

    public RegisteredDidResource(DidManager manager) { this.manager = manager; }

    @jakarta.inject.Inject io.unlockit.application.did.manager.DidListingManager listings;

    @GET
    public io.unlockit.application.did.manager.DidListingManager.Listing list(
            @QueryParam("partyId") String partyId,
            @jakarta.ws.rs.BeanParam ListingParameters parameters) {
        return parameters.list(listings, "registered-dids", partyId);
    }

    @GET
    @Path("/{did}")
    public RegisteredDidRepresentation resolve(@Encoded @PathParam("did") String encodedDidSegment) {
        return DidRepresentationMapper.toRepresentation(manager.resolveRegistered(encodedDidSegment));
    }
}
