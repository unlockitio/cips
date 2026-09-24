package io.unlockit.domain.credential.exception;

public final class CredentialRegistryNotFoundException extends RuntimeException {
  public CredentialRegistryNotFoundException(String registryId) {
    super("credential registry not found");
  }
}
