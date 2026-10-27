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
        new ObjectMapper().createArrayNode().add("https://www.w3.org/ns/credentials/v2"),
        "credential", new ObjectMapper().createArrayNode().add("VerifiableCredential"), "issuer",
        new ObjectMapper().createArrayNode().add(json), null, null);
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
        .body("credential['@context'][0]", equalTo("https://www.w3.org/ns/credentials/v2"))
        .body("credential.type[0]", equalTo("VerifiableCredential"))
         .body("credential.issuer", equalTo("issuer"))
         .body("registration", org.hamcrest.Matchers.nullValue())
         .body("verification", org.hamcrest.Matchers.nullValue());
    given().queryParam("page", "0").queryParam("pageSize", "50").get("/v1/credentials").then()
        .statusCode(200).body("items", hasSize(1)).body("page", equalTo(0))
         .body("items[0].credential.type[0]", equalTo("VerifiableCredential"))
         .body("items[0].verification", org.hamcrest.Matchers.nullValue());
  }

  @Test
  void getsRegisteredCredentialAndPageFromCanonicalRoutes() {
    when(manager.getRegistered("credential", null)).thenReturn(registered);
    when(manager.listRegistered("0", "50", null, new io.unlockit.domain.credential.query.CredentialFilters(null, null, null, null, null, null, null, null, null, null)))
        .thenReturn(new RegisteredCredentialPageResponse(List.of(registered), 0, 50, false));

    given().get("/v1/registered-credentials/credential").then().statusCode(200)
        .body("credential.id", equalTo("credential"))
        .body("credential.type[0]", equalTo("VerifiableCredential"))
         .body("registration.registryAdmin", equalTo("admin"))
         .body("verification", org.hamcrest.Matchers.nullValue());
    given().queryParam("page", "0").queryParam("pageSize", "50")
        .get("/v1/registered-credentials").then().statusCode(200).body("items", hasSize(1))
        .body("items[0].credential.type[0]", equalTo("VerifiableCredential"))
         .body("items[0].registration.registryAdmin", equalTo("admin"))
         .body("items[0].verification", org.hamcrest.Matchers.nullValue());
  }

  @Test
  void preservesIndependentIntrinsicBoundsAndRegistrationExpiryInHttpResponses() {
    var json = new ObjectMapper().createObjectNode();
    var projection = new CredentialProjection(new ObjectMapper().createArrayNode().add("https://www.w3.org/ns/credentials/v2"),
        "credential", new ObjectMapper().createArrayNode().add("VerifiableCredential"), "issuer",
        new ObjectMapper().createArrayNode().add(json),
        "2020-01-01T00:00:00Z", "2099-01-01T00:00:00Z");
    var intrinsic = new CredentialResponse("contract", "credential", "active", projection);
    var registeredProjection = new RegisteredCredentialResponse("contract", "credential", "active",
        projection, new RegistrationResponse("admin", "2020-01-02T00:00:00Z",
            "2021-01-01T00:00:00Z", json));
    when(manager.get("credential", null)).thenReturn(intrinsic);
    when(manager.getRegistered("credential", null)).thenReturn(registeredProjection);

    given().get("/v1/credentials/credential").then().statusCode(200)
        .body("credential.validFrom", equalTo("2020-01-01T00:00:00Z"))
        .body("credential.validUntil", equalTo("2099-01-01T00:00:00Z"))
        .body("lifecycleState", equalTo("active"))
        .body("registration", org.hamcrest.Matchers.nullValue());
    given().get("/v1/registered-credentials/credential").then().statusCode(200)
        .body("credential.validFrom", equalTo("2020-01-01T00:00:00Z"))
        .body("credential.validUntil", equalTo("2099-01-01T00:00:00Z"))
        .body("registration.expiresAt", equalTo("2021-01-01T00:00:00Z"))
        .body("lifecycleState", equalTo("active"));
  }

  @Test
  void projectsOptionalAndOutOfRangeIntrinsicBoundsWithoutFilteringRawReads() {
    var json = new ObjectMapper().createObjectNode();
    var cases = List.of(
        new String[] {"open", null, null},
        new String[] {"future", "2099-01-01T00:00:00Z", null},
        new String[] {"started", "2020-01-01T00:00:00Z", null},
        new String[] {"ended", null, "2020-01-01T00:00:00Z"},
        new String[] {"bounded", "2020-01-01T00:00:00Z", "2099-01-01T00:00:00Z"});
    for (String[] bounds : cases) {
      var projection = new CredentialProjection(new ObjectMapper().createArrayNode().add("https://www.w3.org/ns/credentials/v2"),
          bounds[0], new ObjectMapper().createArrayNode().add("VerifiableCredential"), "issuer",
          new ObjectMapper().createArrayNode().add(json),
          bounds[1], bounds[2]);
      when(manager.get(bounds[0], null))
          .thenReturn(new CredentialResponse("contract-" + bounds[0], bounds[0], "active", projection));
      var body = given().get("/v1/credentials/" + bounds[0]).then().statusCode(200)
          .body("credential.validFrom", equalTo(bounds[1]))
          .body("credential.validUntil", equalTo(bounds[2]))
          .body("lifecycleState", equalTo("active")).extract().asString();
      try {
        var projected = new ObjectMapper().readTree(body).path("credential");
        org.junit.jupiter.api.Assertions.assertEquals(bounds[1] != null, projected.has("validFrom"));
        org.junit.jupiter.api.Assertions.assertEquals(bounds[2] != null, projected.has("validUntil"));
      } catch (java.io.IOException e) {
        throw new AssertionError(e);
      }
    }
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
  void evaluatesSingularReadsAndRejectsCollectionVerification() {
    var unsigned = new CredentialResponse("contract", "credential", "archived", credential.credential(), null, null);
    var bad = new CredentialResponse("bad-contract", "bad", "archived", credential.credential(), null, null,
        "broken.jwt", "application/vc+jwt");
    var unsupported = new RegisteredCredentialResponse("contract", "credential", "archived",
        registered.credential(), registered.registration(), null, null, "opaque", "application/vc+cose");
    when(manager.get("credential", "archived")).thenReturn(unsigned);
    when(manager.get("bad", "archived")).thenReturn(bad);
    when(manager.getRegistered("credential", "archived")).thenReturn(unsupported);
    var filters = new io.unlockit.domain.credential.query.CredentialFilters("archived", null, null, null, null,
        null, null, null, null, null);
    when(manager.list("1", "2", "token", filters)).thenReturn(
        new CredentialPageResponse(List.of(unsigned, bad), 1, 2, true, "123", "next"));
    when(manager.listRegistered("1", "2", "token", filters)).thenReturn(
        new RegisteredCredentialPageResponse(List.of(unsupported), 1, 2, false, "123", null));

    for (String route : List.of("credentials", "registered-credentials")) {
      String single = "/v1/" + route + "/credential";
      for (String suffix : List.of("", "?verify=false")) {
        given().queryParam("state", "archived").get(single + suffix).then().statusCode(200)
            .header("Cache-Control", org.hamcrest.Matchers.not(equalTo("no-store")))
            .body("credentialId", equalTo("credential"))
            .body("record", org.hamcrest.Matchers.nullValue());
      }
      given().queryParam("state", "archived").queryParam("verify", true).get(single).then()
          .statusCode(200).header("Cache-Control", org.hamcrest.Matchers.containsString("no-store"))
          .body("record.lifecycleState", equalTo("archived"))
          .body("evaluation.status", equalTo("INDETERMINATE"))
          .body("evaluation.schemaValidation.status", equalTo("Indeterminate"));
      String page = "/v1/" + route;
      for (String suffix : List.of("", "?verify=false")) {
        given().queryParam("state", "archived").queryParam("page", "1")
            .queryParam("pageSize", "2").queryParam("nextPageToken", "token")
            .get(page + suffix).then().statusCode(200)
            .body("page", equalTo(1)).body("pageSize", equalTo(2))
            .body("snapshotOffset", equalTo("123"))
            .body("items[0].credentialId", equalTo("credential"))
            .body("items[0].record", org.hamcrest.Matchers.nullValue());
      }
      given().queryParam("verify", true).get(page).then().statusCode(400)
          .body("error", equalTo("verify=true is unsupported on collection reads"));
    }
    given().queryParam("state", "archived").queryParam("verify", true)
        .get("/v1/credentials/bad").then().statusCode(200)
        .body("record.credentialId", equalTo("bad"))
        .body("evaluation.status", equalTo("FAILED"));
    org.mockito.Mockito.verify(manager, org.mockito.Mockito.never()).list(null, null, null,
        new io.unlockit.domain.credential.query.CredentialFilters(null, null, null, null, null, null, null, null, null, null));
    org.mockito.Mockito.verify(manager, org.mockito.Mockito.never()).listRegistered(null, null, null,
        new io.unlockit.domain.credential.query.CredentialFilters(null, null, null, null, null, null, null, null, null, null));
  }

  @Test
  void apiPrefixIsNotAnImplicitAlias() {
    given().get("/api/v1/credentials/credential").then().statusCode(404);
    given().get("/api/v1/registered-credentials/credential").then().statusCode(404);
  }
}
