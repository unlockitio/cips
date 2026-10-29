package io.unlockit.application.credential.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.unlockit.application.credential.dto.CredentialTypeCollection;
import io.unlockit.application.credential.dto.CredentialTypeItem;
import io.unlockit.application.credential.manager.CredentialTypeManager;
import io.unlockit.domain.credential.exception.InvalidPaginationException;
import io.unlockit.domain.credential.exception.PqsUnavailableException;
import java.sql.SQLException;
import java.util.List;
import org.junit.jupiter.api.Test;

@QuarkusTest
class CredentialTypeResourceTest {
  @InjectMock CredentialTypeManager manager;

  @Test
  void consumerReadsCanonicalRouteAndRepeatableExactIssuerFilter() {
    when(manager.list(List.of("alice", "bob"), "admin", "all", "0", "1"))
        .thenReturn(new CredentialTypeCollection(List.of(new CredentialTypeItem("alice", "DemoType",
            "2026-01-01T00:00:00Z", "2026-01-02T00:00:00Z")), 0, 1, true));
    given().queryParam("issuer", "alice", "bob").queryParam("registryAdmin", "admin")
        .queryParam("state", "all").queryParam("page", "0").queryParam("pageSize", "1")
        .get("/v1/credential-types").then().statusCode(200)
        .body("items", hasSize(1), "items[0].issuer", equalTo("alice"),
            "items[0].credentialType", equalTo("DemoType"),
            "items[0].firstObservedAt", equalTo("2026-01-01T00:00:00Z"),
            "hasNext", equalTo(true), "page", equalTo(0), "pageSize", equalTo(1))
        .body("items[0].credentialCount", nullValue(), "snapshotOffset", nullValue());
  }

  @Test
  void emptyAndInvalidAndUnavailableResponses() {
    when(manager.list(List.of(), null, null, null, null))
        .thenReturn(new CredentialTypeCollection(List.of(), 0, 50, false));
    given().get("/v1/credential-types").then().statusCode(200)
        .body("items", hasSize(0), "hasNext", equalTo(false));
    when(manager.list(List.of(), null, null, "bad", null))
        .thenThrow(new InvalidPaginationException("invalid page"));
    given().queryParam("page", "bad").get("/v1/credential-types").then()
        .statusCode(400).body("error", equalTo("invalid page"));
    given().queryParam("nextPageToken", "token").get("/v1/credential-types").then()
        .statusCode(400).body("error", containsString("do not accept snapshot tokens"));
    when(manager.list(List.of(), null, "archived", null, null))
        .thenThrow(new PqsUnavailableException(new SQLException("offline")));
    given().queryParam("state", "archived").get("/v1/credential-types").then()
        .statusCode(503).body("error", equalTo("PQS query failed"));
    given().queryParam("lifecycleScope", "archived").get("/v1/credential-types").then()
        .statusCode(503).body("error", equalTo("PQS query failed"));
    given().queryParam("state", "active").queryParam("lifecycleScope", "archived")
        .get("/v1/credential-types").then().statusCode(400)
        .body("error", equalTo("conflicting state and lifecycleScope values"));
  }
}
