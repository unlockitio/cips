package io.unlockit.domain.credential.exception;

public final class InvalidPaginationException extends RuntimeException {
  public InvalidPaginationException(String message) {
    super(message);
  }

  public InvalidPaginationException(String message, Throwable cause) {
    super(message, cause);
  }
}
