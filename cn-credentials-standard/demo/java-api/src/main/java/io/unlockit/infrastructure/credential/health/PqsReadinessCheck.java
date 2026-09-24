package io.unlockit.infrastructure.credential.health;

import io.unlockit.domain.credential.exception.PqsUnavailableException;
import io.unlockit.domain.credential.query.CredentialQueryClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

@Readiness
@ApplicationScoped
public class PqsReadinessCheck implements HealthCheck {
  private final CredentialQueryClient queryClient;

  @Inject
  public PqsReadinessCheck(CredentialQueryClient queryClient) {
    this.queryClient = queryClient;
  }

  @Override
  public HealthCheckResponse call() {
    try {
      queryClient.checkCredentialProjections();
      return HealthCheckResponse.up("PQS credential projections");
    } catch (PqsUnavailableException exception) {
      return HealthCheckResponse.down("PQS credential projections");
    }
  }
}
