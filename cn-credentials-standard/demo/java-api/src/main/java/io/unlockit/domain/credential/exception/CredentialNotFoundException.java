package io.unlockit.domain.credential.exception;

public final class CredentialNotFoundException extends RuntimeException {
  public CredentialNotFoundException(String credentialId) {
    super("credential not found");
  }
}
