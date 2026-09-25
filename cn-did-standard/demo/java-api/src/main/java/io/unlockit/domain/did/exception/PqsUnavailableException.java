package io.unlockit.domain.did.exception;

public class PqsUnavailableException extends RuntimeException {
    public PqsUnavailableException(Throwable cause) {
        super("PQS query failed", cause);
    }
}
