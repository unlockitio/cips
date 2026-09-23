package io.unlockit.domain.credential.exception;

public final class DuplicateCredentialException extends RuntimeException {
  public DuplicateCredentialException(String credentialId) {
    super("duplicate logical credential ID");
  }
}
