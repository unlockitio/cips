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
import io.unlockit.application.credential.manager.CredentialListingManager;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class CredentialResourceTest {
  @InjectMock CredentialListingManager manager;
  private CredentialResponse credential;
  private RegisteredCredentialResponse registered;

  @BeforeEach
  void setUp() {
    var json = new ObjectMapper().createObjectNode();
    var projection = new CredentialProjection(
        "credential", json, json, json, json, json, null, null);
    credential = new CredentialResponse("contract", "credential", "active", projection);
    registered = new RegisteredCredentialResponse(
        "contract", "credential", "active", projection,
        new RegistrationResponse("admin", "2026-09-18T00:00:00Z", null, json));
  }

  @Test
  void getsIntrinsicCredentialAndPageFromCanonicalRoutes() {
    when(manager.get("credential", null)).thenReturn(credential);
    when(manager.list("0", "50", null, new io.unlockit.domain.credential.query.CredentialFilters(null, null, null, null, null, null, null, null, null, null)))
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
    when(manager.getRegistered("credential", null)).thenReturn(registered);
    when(manager.listRegistered("0", "50", null, new io.unlockit.domain.credential.query.CredentialFilters(null, null, null, null, null, null, null, null, null, null)))
        .thenReturn(new RegisteredCredentialPageResponse(List.of(registered), 0, 50, false));

    given().get("/v1/registered-credentials/credential").then().statusCode(200)
        .body("credential.id", equalTo("credential"))
        .body("registration.registryAdmin", equalTo("admin"));
    given().queryParam("page", "0").queryParam("pageSize", "50")
        .get("/v1/registered-credentials").then().statusCode(200).body("items", hasSize(1));
  }

  @Test
  void bindsTokenAndPageJumpForBothCollections() {
    var filters = new io.unlockit.domain.credential.query.CredentialFilters(
        "all", null, null, null, null, null, null, null, null, null);
    when(manager.list("7", "50", "signed-token", filters))
        .thenReturn(new CredentialPageResponse(List.of(credential), 7, 50, false));
    when(manager.listRegistered("7", "50", "signed-token", filters))
        .thenReturn(new RegisteredCredentialPageResponse(List.of(registered), 7, 50, false));

    for (String resource : List.of("credentials", "registered-credentials")) {
      given().queryParam("state", "all").queryParam("page", "7").queryParam("pageSize", "50")
          .queryParam("nextPageToken", "signed-token").get("/v1/" + resource).then()
          .statusCode(200).body("page", equalTo(7)).body("items", hasSize(1));
    }
    org.mockito.Mockito.verify(manager).list("7", "50", "signed-token", filters);
    org.mockito.Mockito.verify(manager).listRegistered("7", "50", "signed-token", filters);
  }

  @Test
  void bindsExactTemporalParametersForBothCollections() {
    var filters = new io.unlockit.domain.credential.query.CredentialFilters("all",
        "2026-01-01T01:02:03.123456+01:00", "2026-01-02T01:02:03.654321+01:00",
        "2026-02-01T04:05:06.123456Z", "2026-02-02T04:05:06.654321Z",
        null, null, null, null, null);
    when(manager.list(null, null, null, filters))
        .thenReturn(new CredentialPageResponse(List.of(credential), 0, 50, false));
    when(manager.listRegistered(null, null, null, filters))
        .thenReturn(new RegisteredCredentialPageResponse(List.of(registered), 0, 50, false));

    for (String resource : List.of("credentials", "registered-credentials")) {
      given().queryParam("state", "all")
          .queryParam("createdFrom", filters.createdFrom()).queryParam("createdUntil", filters.createdUntil())
          .queryParam("archivedFrom", filters.archivedFrom()).queryParam("archivedUntil", filters.archivedUntil())
          .get("/v1/" + resource).then().statusCode(200).body("items", hasSize(1));
    }
    org.mockito.Mockito.verify(manager).list(null, null, null, filters);
    org.mockito.Mockito.verify(manager).listRegistered(null, null, null, filters);
  }

  @Test
  void apiPrefixIsNotAnImplicitAlias() {
    given().get("/api/v1/credentials/credential").then().statusCode(404);
    given().get("/api/v1/registered-credentials/credential").then().statusCode(404);
  }
}
