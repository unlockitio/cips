package io.unlockit.domain.credential.exception;

public final class PqsUnavailableException extends RuntimeException {
  public PqsUnavailableException(Throwable cause) {
    super("PQS query failed", cause);
  }
}
