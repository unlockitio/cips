package io.unlockit.application.did.api;

import io.unlockit.application.did.api.representation.ErrorRepresentation;
import io.unlockit.application.did.manager.DidManager.DidNotFoundException;
import io.unlockit.domain.did.exception.DuplicateDidException;
import io.unlockit.domain.did.exception.PqsUnavailableException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class DidErrorBoundary implements ExceptionMapper<RuntimeException> {
    @Override
    public Response toResponse(RuntimeException exception) {
        if (exception instanceof WebApplicationException webException) {
            return webException.getResponse();
        }
        if (exception instanceof DidNotFoundException) {
            return response(Response.Status.NOT_FOUND, "did_not_found", exception.getMessage());
        }
        if (exception instanceof DuplicateDidException) {
            return response(Response.Status.CONFLICT, "duplicate_active_did", exception.getMessage());
        }
        if (exception instanceof PqsUnavailableException) {
            return response(Response.Status.SERVICE_UNAVAILABLE, "pqs_unavailable", "PQS query failed");
        }
        if (exception instanceof io.unlockit.application.did.manager.ListingToken.ListingException) {
            return response(Response.Status.BAD_REQUEST, exception.getMessage(), exception.getMessage());
        }
        if (exception instanceof java.time.format.DateTimeParseException) {
            return response(Response.Status.BAD_REQUEST, "invalid_request", "Time filters must be ISO-8601 instants");
        }
        if (exception instanceof IllegalArgumentException) {
            return response(Response.Status.BAD_REQUEST, "invalid_request", exception.getMessage());
        }
        return response(Response.Status.INTERNAL_SERVER_ERROR, "internal_error", "Unexpected DID demo error");
    }

    private Response response(Response.Status status, String error, String message) {
        return Response.status(status)
                .entity(new ErrorRepresentation(status.getStatusCode(), error, message))
                .type(jakarta.ws.rs.core.MediaType.APPLICATION_JSON_TYPE)
                .build();
    }
}
