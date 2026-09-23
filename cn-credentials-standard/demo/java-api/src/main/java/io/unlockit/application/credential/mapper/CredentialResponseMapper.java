package io.unlockit.application.credential.mapper;

import io.unlockit.application.credential.dto.CredentialProjection;
import io.unlockit.application.credential.dto.CredentialResponse;
import io.unlockit.application.credential.dto.RegisteredCredentialResponse;
import io.unlockit.application.credential.dto.RegistrationResponse;
import io.unlockit.domain.credential.model.Credential;
import io.unlockit.domain.credential.model.RegisteredCredential;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class CredentialResponseMapper {
  public CredentialResponse toResponse(Credential credential) {
    return new CredentialResponse(
        credential.contractId(), credential.credentialId(), "active", projection(credential));
  }

  public RegisteredCredentialResponse toResponse(RegisteredCredential registeredCredential) {
    var credential = registeredCredential.credential();
    var registration = registeredCredential.registration();
    return new RegisteredCredentialResponse(
        credential.contractId(),
        credential.credentialId(),
        "active",
        projection(credential),
        new RegistrationResponse(
            registration.registryAdmin(),
            registration.registeredAt(),
            registration.expiresAt(),
            registration.meta()));
  }

  private static CredentialProjection projection(Credential credential) {
    return new CredentialProjection(
        credential.credentialId(),
        credential.issuer(),
        credential.credentialTypes(),
        credential.subjects(),
        credential.holders(),
        credential.validFrom(),
        credential.validUntil());
  }
}
