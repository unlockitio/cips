package io.unlockit.application.credential.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.unlockit.domain.credential.model.Credential;
import io.unlockit.domain.credential.query.CredentialQueryClient;
import java.util.List;
import org.junit.jupiter.api.Test;

@QuarkusTest
class NameResolutionResourceTest {
  @InjectMock CredentialQueryClient client;
  private final ObjectMapper json = new ObjectMapper();

  private Credential name(String party, String name, String from, String until) throws Exception {
    JsonNode subject = json.readTree("""
        [{"id":{"tag":"W3C_VC_Identifier_Party","value":"%s"},
          "claims":{"demo.example/name":{"tag":"AV_Text","value":"%s"}}}]
        """.formatted(party, name));
    return new Credential("contract-" + name, "urn:demo:name:" + name,
        json.readTree("{\"tag\":\"W3C_VC_Identifier_Party\",\"value\":\"issuer\"}"),
        json.readTree("[\"VerifiableCredential\",\"DemoPartyName\"]"), subject,
        json.readTree("[]"), json.readTree("{\"hd\":\"issuer\",\"tl\":[]}"), from, until);
  }

  @Test
  void resolvesFromCredentialSubjectAndReverseResolves() throws Exception {
    var alice = name("Alice::namespace", "alice.canton", "2020-01-01T00:00:00Z", "2099-01-01T00:00:00Z");
    when(client.findActiveNames("alice.canton", null)).thenReturn(List.of(alice));
    when(client.findActiveNames(null, "Alice::namespace")).thenReturn(List.of(alice));
    given().queryParam("name", "alice.canton").queryParam("network", "localnet")
        .get("/v1/resolve").then().statusCode(200)
        .body("fqpn", equalTo("localnet:demo:alice.canton"))
        .body("party_id", equalTo("Alice::namespace"))
        .body("display_name", equalTo("alice.canton"))
        .body("valid_until", equalTo("2099-01-01T00:00:00Z"));
    given().queryParam("party", "Alice::namespace").queryParam("network", "localnet")
        .queryParam("source", "credential").get("/v1/reverse-resolve").then()
        .statusCode(200).body("$", hasSize(1)).body("[0].party_id", equalTo("Alice::namespace"));
  }

  @Test
  void rejectsMissingOrMismatchedNetworkAndUnsupportedSource() {
    given().queryParam("name", "alice.canton").get("/v1/resolve").then().statusCode(400);
    given().queryParam("name", "alice.canton").queryParam("network", "mainnet")
        .get("/v1/resolve").then().statusCode(400);
    given().queryParam("party", "Alice::namespace").queryParam("network", "testnet")
        .get("/v1/reverse-resolve").then().statusCode(400);
    given().queryParam("name", "alice.canton").queryParam("network", "localnet")
        .queryParam("source", "did").get("/v1/resolve").then().statusCode(422);
    given().queryParam("party", "Alice::namespace").queryParam("network", "localnet")
        .queryParam("source", "did").get("/v1/reverse-resolve").then().statusCode(422);
    given().queryParam("name", "alice.canton").queryParam("network", "localnet")
        .queryParam("source", "unknown").get("/v1/resolve").then().statusCode(400);
    given().queryParam("party", "Alice::namespace").queryParam("network", "localnet")
        .queryParam("source", "unknown").get("/v1/reverse-resolve").then().statusCode(400);
    given().queryParam("network", "localnet").get("/v1/resolve").then().statusCode(400);
    given().queryParam("network", "localnet").get("/v1/reverse-resolve").then().statusCode(400);
    given().queryParam("name", "two words").queryParam("network", "localnet")
        .get("/v1/resolve").then().statusCode(400);
  }

  @Test
  void missingNameIs404ButReverseMayBeEmpty() {
    when(client.findActiveNames("unknown", null)).thenReturn(List.of());
    when(client.findActiveNames(null, "Unknown::namespace")).thenReturn(List.of());
    given().queryParam("name", "unknown").queryParam("network", "localnet")
        .get("/v1/resolve").then().statusCode(404);
    given().queryParam("party", "Unknown::namespace").queryParam("network", "localnet")
        .get("/v1/reverse-resolve").then().statusCode(200).body("$", hasSize(0));
  }

  @Test
  void duplicateBindingsAreConflictNotArbitraryChoice() throws Exception {
    var alice = name("Alice::namespace", "alice.canton", null, null);
    var other = name("Other::namespace", "alice.canton", null, null);
    when(client.findActiveNames("alice.canton", null)).thenReturn(List.of(alice, other));
    given().queryParam("name", "alice.canton").queryParam("network", "localnet")
        .get("/v1/resolve").then().statusCode(409);
    when(client.findActiveNames(null, "Alice::namespace")).thenReturn(List.of(alice, alice));
    given().queryParam("party", "Alice::namespace").queryParam("network", "localnet")
        .get("/v1/reverse-resolve").then().statusCode(409);
  }

  @Test
  void resolvesOpenEndedAndPastStartWithoutEnd() throws Exception {
    var open = name("Alice::namespace", "open.canton", null, null);
    var started = name("Alice::namespace", "started.canton", "2020-01-01T00:00:00Z", null);
    when(client.findActiveNames("open.canton", null)).thenReturn(List.of(open));
    when(client.findActiveNames("started.canton", null)).thenReturn(List.of(started));
    when(client.findActiveNames(null, "Alice::namespace")).thenReturn(List.of(open, started));

    for (String name : List.of("open.canton", "started.canton")) {
      given().queryParam("name", name).queryParam("network", "localnet")
          .get("/v1/resolve").then().statusCode(200)
          .body("display_name", equalTo(name))
          .body("valid_until", org.hamcrest.Matchers.nullValue());
    }
    given().queryParam("party", "Alice::namespace").queryParam("network", "localnet")
        .get("/v1/reverse-resolve").then().statusCode(200).body("$", hasSize(2));
  }

  @Test
  void futureStartAndPastEndAreExcludedIndependently() throws Exception {
    var future = name("Alice::namespace", "future.canton", "2099-01-01T00:00:00Z", null);
    var ended = name("Alice::namespace", "ended.canton", null, "2020-01-01T00:00:00Z");
    var current = name("Alice::namespace", "current.canton",
        "2020-01-01T00:00:00Z", "2099-01-01T00:00:00Z");
    when(client.findActiveNames("future.canton", null)).thenReturn(List.of(future));
    when(client.findActiveNames("ended.canton", null)).thenReturn(List.of(ended));
    when(client.findActiveNames("current.canton", null)).thenReturn(List.of(current));
    when(client.findActiveNames(null, "Alice::namespace"))
        .thenReturn(List.of(future, ended, current));

    for (String excluded : List.of("future.canton", "ended.canton")) {
      given().queryParam("name", excluded).queryParam("network", "localnet")
          .get("/v1/resolve").then().statusCode(404);
    }
    given().queryParam("name", "current.canton").queryParam("network", "localnet")
        .get("/v1/resolve").then().statusCode(200)
        .body("valid_until", equalTo("2099-01-01T00:00:00Z"));
    given().queryParam("party", "Alice::namespace").queryParam("network", "localnet")
        .get("/v1/reverse-resolve").then().statusCode(200)
        .body("$", hasSize(1)).body("[0].display_name", equalTo("current.canton"));
  }

  @Test
  void expiredOrMissingSubjectIdCannotResolve() throws Exception {
    var expired = name("Alice::namespace", "alice.canton", null, "2020-01-01T00:00:00Z");
    var noSubject = name("Alice::namespace", "alice.canton", null, null);
    var subjects = json.readTree("[{\"id\":null,\"claims\":{\"demo.example/name\":{\"tag\":\"AV_Text\",\"value\":\"alice.canton\"}}}]");
    noSubject = new Credential(noSubject.contractId(), noSubject.credentialId(), noSubject.issuer(),
        noSubject.credentialTypes(), subjects, noSubject.holders(), noSubject.anchorers(), null, null);
    when(client.findActiveNames("alice.canton", null)).thenReturn(List.of(expired, noSubject));
    given().queryParam("name", "alice.canton").queryParam("network", "localnet")
        .get("/v1/resolve").then().statusCode(404);
  }
}
