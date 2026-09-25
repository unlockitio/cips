package io.unlockit.application.bft.api;

import io.unlockit.application.bft.api.representation.BftErrorRepresentation;
import io.unlockit.domain.bft.BftReadException;
import io.unlockit.domain.bft.SourceObservation;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class BftErrorBoundary implements ExceptionMapper<RuntimeException> {
    @Override
    public Response toResponse(RuntimeException exception) {
        if (exception instanceof BftReadException bft) {
            return response(bft.status(), bft.error(), bft.getMessage(), bft.sources());
        }
        if (exception instanceof IllegalArgumentException) {
            return response(400, "invalid_request", exception.getMessage(), java.util.List.of());
        }
        return response(500, "internal_error", "Unexpected BFT reader error", java.util.List.of());
    }

    private Response response(int status, String error, String message, java.util.List<SourceObservation> sources) {
        var diagnostics = sources.stream().limit(3).map(source ->
                new BftErrorRepresentation.SourceDiagnosticRepresentation(source.advertisedUri(), source.priority(),
                        source.status(), source.outcome(), source.digest(), source.durationMillis())).toList();
        return Response.status(status).type(MediaType.APPLICATION_JSON_TYPE)
                .entity(new BftErrorRepresentation(status, error, message, diagnostics)).build();
    }
}
