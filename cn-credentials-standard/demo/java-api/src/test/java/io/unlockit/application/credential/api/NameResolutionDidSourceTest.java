package io.unlockit.application.credential.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.unlockit.domain.credential.did.DidNameAssertionClient;
import io.unlockit.domain.credential.did.DidNameAssertionClient.DidNameAssertion;
import io.unlockit.domain.credential.exception.DidUnavailableException;
import io.unlockit.domain.credential.model.Credential;
import io.unlockit.domain.credential.query.CredentialQueryClient;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class NameResolutionDidSourceTest {
  @InjectMock CredentialQueryClient client;
  @InjectMock DidNameAssertionClient dids;
  private final ObjectMapper json = new ObjectMapper();

  @BeforeEach
  void enableDidSource() {
    when(dids.enabled()).thenReturn(true);
  }

  private Credential name(String party, String name) throws Exception {
    JsonNode subject = json.readTree("""
        [{"id":{"tag":"W3C_VC_Identifier_Party","value":"%s"},
          "claims":{"demo.example/name":{"tag":"AV_Text","value":"%s"}}}]
        """.formatted(party, name));
    return new Credential("contract-" + party, "urn:demo:name:" + name,
        json.readTree("{\"tag\":\"W3C_VC_Identifier_Party\",\"value\":\"issuer\"}"),
        json.readTree("[\"VerifiableCredential\",\"DemoPartyName\"]"), subject,
        json.readTree("[]"), json.readTree("{\"hd\":\"issuer\",\"tl\":[]}"), null, null);
  }

  @Test
  void agreeingAssertionAndAllocationResolve() throws Exception {
    var alice = name("Alice::namespace", "alice.canton");
    when(client.findActiveNames("alice.canton", null)).thenReturn(List.of(alice));
    when(client.findActiveNames(null, "Alice::namespace")).thenReturn(List.of(alice));
    when(dids.findByAlias("localnet", "alice.canton")).thenReturn(
        List.of(new DidNameAssertion("did:canton:Alice", "urn:fqpn:localnet:demo:alice.canton", "Alice::namespace")));
    when(dids.findBySubject("Alice::namespace")).thenReturn(
        List.of(new DidNameAssertion("did:canton:Alice", "urn:fqpn:localnet:demo:alice.canton", "Alice::namespace")));
    given().queryParam("name", "alice.canton").queryParam("network", "localnet").queryParam("source", "did")
        .get("/v1/resolve").then().statusCode(200)
        .body("party_id", equalTo("Alice::namespace"))
        .body("fqpn", equalTo("localnet:demo:alice.canton"));
    given().queryParam("party", "Alice::namespace").queryParam("network", "localnet").queryParam("source", "did")
        .get("/v1/reverse-resolve").then().statusCode(200).body("$", hasSize(1))
        .body("[0].fqpn", equalTo("localnet:demo:alice.canton"));
  }

  @Test
  void assertionAgainstAnotherAllocationIsConflict() throws Exception {
    when(client.findActiveNames("alice.canton", null)).thenReturn(List.of(name("Alice::namespace", "alice.canton")));
    when(dids.findByAlias("localnet", "alice.canton")).thenReturn(
        List.of(new DidNameAssertion("did:canton:Mallory", "urn:fqpn:localnet:demo:alice.canton", "Mallory::namespace")));
    given().queryParam("name", "alice.canton").queryParam("network", "localnet").queryParam("source", "did")
        .get("/v1/resolve").then().statusCode(409);
  }

  @Test
  void duplicateConflictingAliasesAreConflict() throws Exception {
    when(client.findActiveNames("alice.canton", null)).thenReturn(List.of(name("Alice::namespace", "alice.canton")));
    when(dids.findByAlias("localnet", "alice.canton")).thenReturn(List.of(
        new DidNameAssertion("did:canton:Alice", "urn:fqpn:localnet:demo:alice.canton", "Alice::namespace"),
        new DidNameAssertion("did:canton:Mallory", "urn:fqpn:localnet:demo:alice.canton", "Mallory::namespace")));
    given().queryParam("name", "alice.canton").queryParam("network", "localnet").queryParam("source", "did")
        .get("/v1/resolve").then().statusCode(409);
  }

  @Test
  void assertionWithoutAllocationIsNotFound() throws Exception {
    when(client.findActiveNames("ghost.canton", null)).thenReturn(List.of());
    when(dids.findByAlias("localnet", "ghost.canton")).thenReturn(
        List.of(new DidNameAssertion("did:canton:Ghost", "urn:fqpn:localnet:demo:ghost.canton", "Ghost::namespace")));
    given().queryParam("name", "ghost.canton").queryParam("network", "localnet").queryParam("source", "did")
        .get("/v1/resolve").then().statusCode(404);
  }

  @Test
  void allocationWithoutAssertionIsNotFoundAndReverseIsEmpty() throws Exception {
    var alice = name("Alice::namespace", "alice.canton");
    when(client.findActiveNames("alice.canton", null)).thenReturn(List.of(alice));
    when(client.findActiveNames(null, "Alice::namespace")).thenReturn(List.of(alice));
    when(dids.findByAlias("localnet", "alice.canton")).thenReturn(List.of());
    when(dids.findBySubject("Alice::namespace")).thenReturn(List.of());
    given().queryParam("name", "alice.canton").queryParam("network", "localnet").queryParam("source", "did")
        .get("/v1/resolve").then().statusCode(404);
    given().queryParam("party", "Alice::namespace").queryParam("network", "localnet").queryParam("source", "did")
        .get("/v1/reverse-resolve").then().statusCode(200).body("$", hasSize(0));
  }

  @Test
  void assertionAboutAnotherPartyNeverAddsReverseNames() throws Exception {
    var alice = name("Alice::namespace", "alice.canton");
    when(client.findActiveNames(null, "Alice::namespace")).thenReturn(List.of(alice));
    when(dids.findBySubject("Alice::namespace")).thenReturn(
        List.of(new DidNameAssertion("did:canton:Mallory", "urn:fqpn:localnet:demo:alice.canton", "Mallory::namespace")));
    given().queryParam("party", "Alice::namespace").queryParam("network", "localnet").queryParam("source", "did")
        .get("/v1/reverse-resolve").then().statusCode(200).body("$", hasSize(0));
  }

  @Test
  void downstreamFailureIsUnavailableNotUnsupported() throws Exception {
    when(client.findActiveNames("alice.canton", null)).thenReturn(List.of(name("Alice::namespace", "alice.canton")));
    when(dids.findByAlias("localnet", "alice.canton"))
        .thenThrow(new DidUnavailableException("DID assertion lookup failed", null));
    given().queryParam("name", "alice.canton").queryParam("network", "localnet").queryParam("source", "did")
        .get("/v1/resolve").then().statusCode(503);
  }

  @Test
  void disabledCapabilityRemains422() throws Exception {
    when(dids.enabled()).thenReturn(false);
    given().queryParam("name", "alice.canton").queryParam("network", "localnet").queryParam("source", "did")
        .get("/v1/resolve").then().statusCode(422);
    given().queryParam("party", "Alice::namespace").queryParam("network", "localnet").queryParam("source", "did")
        .get("/v1/reverse-resolve").then().statusCode(422);
  }
}
