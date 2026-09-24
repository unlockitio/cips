package io.unlockit.application.credential.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;

import io.unlockit.domain.credential.exception.CredentialRegistryNotFoundException;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.unlockit.application.credential.dto.CredentialRegistryPageResponse;
import io.unlockit.application.credential.dto.CredentialRegistryResponse;
import io.unlockit.application.credential.dto.IssuanceFactoryResponse;
import io.unlockit.application.credential.manager.CredentialRegistryManager;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class CredentialRegistryResourceTest {
  @InjectMock CredentialRegistryManager manager;
  private CredentialRegistryResponse registry;

  @BeforeEach
  void setUp() {
    var capabilities =
        new CredentialRegistryResponse.FamilyCapabilities(
            true, false, false, new String[] {"active"}, new String[] {}, 50, 100);
    registry =
        new CredentialRegistryResponse(
            "admin",
            "1.0.0-draft",
            capabilities,
            capabilities,
            List.of(new IssuanceFactoryResponse("contract", "issuer")));
  }

  @Test
  void getsCollectionAndExactItem() {
    when(manager.findPage("0", "50"))
        .thenReturn(new CredentialRegistryPageResponse(List.of(registry), 0, 50, false));
    when(manager.findByRegistryId("admin")).thenReturn(registry);

    given()
        .queryParam("page", "0")
        .queryParam("pageSize", "50")
        .get("/v1/credential-registries")
        .then()
        .statusCode(200)
        .body("items", hasSize(1))
        .body("items[0].registryId", equalTo("admin"))
        .body("items[0].issuanceFactories[0].contractId", equalTo("contract"));
    given()
        .get("/v1/credential-registries/admin")
        .then()
        .statusCode(200)
        .body("registryId", equalTo("admin"));
  }

  @Test
  void returnsNotFoundForUnknownRegistry() {
    when(manager.findByRegistryId("missing"))
        .thenThrow(new CredentialRegistryNotFoundException("missing"));
    given()
        .get("/v1/credential-registries/missing")
        .then()
        .statusCode(404)
        .body("error", equalTo("credential registry not found"));
  }

  @Test
  void removedRegistryInfoRouteIsNotExposed() {
    given().get("/v1/registry-info").then().statusCode(404);
  }
}
