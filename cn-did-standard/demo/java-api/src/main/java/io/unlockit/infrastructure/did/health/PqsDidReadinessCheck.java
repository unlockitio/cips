package io.unlockit.infrastructure.did.health;

import io.unlockit.domain.did.exception.PqsUnavailableException;
import io.unlockit.infrastructure.did.pqs.PqsDidQueryClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

@Readiness
@ApplicationScoped
public class PqsDidReadinessCheck implements HealthCheck {
    private final PqsDidQueryClient queryClient;

    @Inject
    public PqsDidReadinessCheck(PqsDidQueryClient queryClient) {
        this.queryClient = queryClient;
    }

    @Override
    public HealthCheckResponse call() {
        try {
            queryClient.checkProjections();
            return HealthCheckResponse.up("PQS DID interface projections");
        } catch (PqsUnavailableException | io.unlockit.application.did.manager.ListingToken.ListingException exception) {
            return HealthCheckResponse.named("PQS joined DID interface projections").down()
                    .withData("requirement", "PQS 3.5 bigint set_latest, validate_offset_exists, active, creates and archives signatures and lifecycle columns")
                    .withData("failure", exception.getCause() instanceof java.sql.SQLException sql
                            ? "SQLSTATE " + sql.getSQLState() : exception.getMessage()).build();
        }
    }
}
