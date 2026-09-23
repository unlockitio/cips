package io.unlockit.infrastructure.credential.health;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.unlockit.domain.credential.exception.PqsUnavailableException;
import io.unlockit.domain.credential.query.CredentialQueryClient;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;

@QuarkusTest
class HealthTest {
  @InjectMock CredentialQueryClient queryClient;

  @Test
  void livenessIsDependencyIndependent() {
    doThrow(new PqsUnavailableException(new SQLException("down")))
        .when(queryClient)
        .checkCredentialProjections();
    given().when().get("/q/health/live").then().statusCode(200).body("status", equalTo("UP"));
  }

  @Test
  void readinessDependsOnRegisteredCredentialProjection() {
    doNothing().when(queryClient).checkCredentialProjections();
    given().when().get("/q/health/ready").then().statusCode(200).body("status", equalTo("UP"));

    doThrow(new PqsUnavailableException(new SQLException("internal detail")))
        .when(queryClient)
        .checkCredentialProjections();
    given().when().get("/q/health/ready").then().statusCode(503).body("status", equalTo("DOWN"));
  }
}
