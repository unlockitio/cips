package io.unlockit.domain.did.exception;

public class DuplicateDidException extends RuntimeException {
    public DuplicateDidException(String did) {
        super("Multiple active contracts found for DID: " + did);
    }
}
