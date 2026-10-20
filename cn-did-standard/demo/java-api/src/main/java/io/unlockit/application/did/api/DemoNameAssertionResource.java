package io.unlockit.application.did.api;

import io.unlockit.domain.did.entity.DemoNameAssertion;
import io.unlockit.infrastructure.did.pqs.PqsDidQueryClient;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;

/**
 * Bounded lookup over self-claimed demo name aliases. Exactly one of alias or
 * subject must be supplied, and the query is indexed on that value rather than
 * scanning the DID collection. Results are unverified self-assertions: a caller
 * must cross-check an authoritative name allocation before trusting them.
 */
@Path("/v1/demo-name-assertions")
@Produces(MediaType.APPLICATION_JSON)
public class DemoNameAssertionResource {
    static final int MAX_MATCHES = 10;
    private final PqsDidQueryClient queryClient;

    public DemoNameAssertionResource(PqsDidQueryClient queryClient) { this.queryClient = queryClient; }

    @GET
    public List<DemoNameAssertion> list(@QueryParam("alias") String alias, @QueryParam("subject") String subject) {
        boolean byAlias = alias != null && !alias.isBlank();
        boolean bySubject = subject != null && !subject.isBlank();
        if (byAlias == bySubject) {
            throw new jakarta.ws.rs.WebApplicationException(
                    Response.status(400).entity(java.util.Map.of("error",
                            "exactly one of alias or subject is required")).build());
        }
        return queryClient.findDemoNameAssertions(byAlias ? alias : null, bySubject ? subject : null, MAX_MATCHES);
    }
}
