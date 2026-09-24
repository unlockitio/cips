package io.unlockit.application.credential.api;

import io.unlockit.application.credential.dto.ErrorResponse;
import io.unlockit.domain.credential.exception.CredentialNotFoundException;
import io.unlockit.domain.credential.exception.CredentialRegistryNotFoundException;
import io.unlockit.domain.credential.exception.DuplicateCredentialException;
import io.unlockit.domain.credential.exception.InvalidPaginationException;
import io.unlockit.domain.credential.exception.PqsUnavailableException;
import io.unlockit.domain.credential.exception.UnsupportedCapabilityException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class CredentialExceptionMapper implements ExceptionMapper<RuntimeException> {
  @Override
  public Response toResponse(RuntimeException exception) {
    if (exception instanceof WebApplicationException webException) {
      return webException.getResponse();
    }
    if (exception instanceof InvalidPaginationException) {
      return response(Response.Status.BAD_REQUEST, exception.getMessage());
    }
    if (exception instanceof CredentialNotFoundException
        || exception instanceof CredentialRegistryNotFoundException) {
      return response(Response.Status.NOT_FOUND, exception.getMessage());
    }
    if (exception instanceof DuplicateCredentialException) {
      return response(Response.Status.CONFLICT, exception.getMessage());
    }
    if (exception instanceof UnsupportedCapabilityException) {
      return response(422, exception.getMessage());
    }
    if (exception instanceof PqsUnavailableException) {
      return response(Response.Status.SERVICE_UNAVAILABLE, "PQS query failed");
    }
    throw exception;
  }

  private static Response response(Response.Status status, String message) {
    return response(status.getStatusCode(), message);
  }

  private static Response response(int status, String message) {
    return Response.status(status).entity(new ErrorResponse(message)).build();
  }
}
