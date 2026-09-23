package io.unlockit.application.credential.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.unlockit.application.credential.dto.CredentialPageResponse;
import io.unlockit.application.credential.dto.CredentialProjection;
import io.unlockit.application.credential.dto.CredentialResponse;
import io.unlockit.application.credential.dto.RegisteredCredentialPageResponse;
import io.unlockit.application.credential.dto.RegisteredCredentialResponse;
import io.unlockit.application.credential.dto.RegistrationResponse;
import io.unlockit.application.credential.manager.CredentialManager;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class CredentialResourceTest {
  @InjectMock CredentialManager manager;
  private CredentialResponse credential;
  private RegisteredCredentialResponse registered;

  @BeforeEach
  void setUp() {
    var json = new ObjectMapper().createObjectNode();
    var projection = new CredentialProjection(
        "credential", json, json, json, json, null, null);
    credential = new CredentialResponse("contract", "credential", "active", projection);
    registered = new RegisteredCredentialResponse(
        "contract", "credential", "active", projection,
        new RegistrationResponse("admin", "2026-09-18T00:00:00Z", null, json));
  }

  @Test
  void getsIntrinsicCredentialAndPageFromCanonicalRoutes() {
    when(manager.findByCredentialId("credential", null)).thenReturn(credential);
    when(manager.findPage("0", "50", null))
        .thenReturn(new CredentialPageResponse(List.of(credential), 0, 50, false));

    given().get("/v1/credentials/credential").then().statusCode(200)
        .body("contractId", equalTo("contract"))
        .body("credential.id", equalTo("credential"))
        .body("registration", org.hamcrest.Matchers.nullValue());
    given().queryParam("page", "0").queryParam("pageSize", "50").get("/v1/credentials").then()
        .statusCode(200).body("items", hasSize(1)).body("page", equalTo(0));
  }

  @Test
  void getsRegisteredCredentialAndPageFromCanonicalRoutes() {
    when(manager.findRegisteredByCredentialId("credential", null)).thenReturn(registered);
    when(manager.findRegisteredPage("0", "50", null))
        .thenReturn(new RegisteredCredentialPageResponse(List.of(registered), 0, 50, false));

    given().get("/v1/registered-credentials/credential").then().statusCode(200)
        .body("credential.id", equalTo("credential"))
        .body("registration.registryAdmin", equalTo("admin"));
    given().queryParam("page", "0").queryParam("pageSize", "50")
        .get("/v1/registered-credentials").then().statusCode(200).body("items", hasSize(1));
  }

  @Test
  void apiPrefixIsNotAnImplicitAlias() {
    given().get("/api/v1/credentials/credential").then().statusCode(404);
    given().get("/api/v1/registered-credentials/credential").then().statusCode(404);
  }
}
