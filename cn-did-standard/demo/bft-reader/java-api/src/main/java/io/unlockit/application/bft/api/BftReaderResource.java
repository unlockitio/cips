package io.unlockit.application.bft.api;

import io.unlockit.application.bft.api.representation.BftReadRepresentation;
import io.unlockit.application.bft.manager.BftReaderManager;
import jakarta.ws.rs.Encoded;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/v1/bft")
@Produces(MediaType.APPLICATION_JSON)
public class BftReaderResource {
    private final BftReaderManager manager;

    public BftReaderResource(BftReaderManager manager) {
        this.manager = manager;
    }

    @GET
    @Path("/dids/{did}")
    public BftReadRepresentation readDid(@Encoded @PathParam("did") String encodedDid) {
        String did = DidPathCodec.decodeCanonicalSegment(encodedDid);
        if (!did.matches("did:canton:[^:/?#\\s]+::[^:/?#\\s]+")) {
            throw new IllegalArgumentException("DID must be a canonical did:canton Party identifier");
        }
        return manager.readDid(did, encodedDid);
    }

}
