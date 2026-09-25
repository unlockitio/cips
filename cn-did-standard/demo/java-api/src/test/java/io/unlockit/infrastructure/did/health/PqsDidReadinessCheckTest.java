package io.unlockit.infrastructure.did.health;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.unlockit.domain.did.exception.PqsUnavailableException;
import io.unlockit.infrastructure.did.pqs.PqsDidQueryClient;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;

@QuarkusTest
class PqsDidReadinessCheckTest {
    @InjectMock PqsDidQueryClient queryClient;

    @Test
    void livenessIsIndependentAndReadinessRequiresAvailableProjections() {
        doThrow(new PqsUnavailableException(new SQLException("down"))).when(queryClient).checkProjections();
        given().get("/q/health/live").then().statusCode(200).body("status", equalTo("UP"));
        given().get("/q/health/ready").then().statusCode(503);
        doNothing().when(queryClient).checkProjections();
        given().get("/q/health/ready").then().statusCode(200).body("status", equalTo("UP"));
    }
}
