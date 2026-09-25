package io.unlockit.application.did.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

import io.quarkus.test.InjectMock;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.unlockit.domain.did.repository.DidRepository;
import io.unlockit.support.TestDidRepository;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class DidResourceTest {
    @TestHTTPResource URI baseUri;
    @InjectMock DidRepository repository;
    @InjectMock io.unlockit.infrastructure.did.pqs.PqsDidQueryClient query;
    @jakarta.inject.Inject com.fasterxml.jackson.databind.ObjectMapper mapper;

    @BeforeEach
    void useExplicitTestRepository() {
        TestDidRepository fixtures = new TestDidRepository();
        org.mockito.Mockito.when(query.latestOffset()).thenReturn(123L);
        org.mockito.Mockito.when(query.findHistory(org.mockito.ArgumentMatchers.anyBoolean(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.nullable(String.class),
                org.mockito.ArgumentMatchers.nullable(String.class), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyLong()))
                .thenAnswer(invocation -> {
                    boolean registered = invocation.getArgument(0);
                    io.unlockit.application.did.manager.DidListingManager.Filters filters = invocation.getArgument(1);
                    int limit = invocation.getArgument(5);
                    long offset = invocation.getArgument(6);
                    java.util.List<?> values = registered
                            ? (filters.partyId() == null ? fixtures.findRegisteredPage(offset, limit)
                                : fixtures.findRegisteredByPartyId(new io.unlockit.domain.did.value_object.PartyId(filters.partyId()), offset, limit))
                            .stream().map(io.unlockit.application.did.mapper.DidRepresentationMapper::toRepresentation).toList()
                            : fixtures.findIntrinsicPage(offset, limit).stream()
                                .map(io.unlockit.application.did.mapper.DidRepresentationMapper::toRepresentation).toList();
                    return values.stream().map(v -> mapper.<com.fasterxml.jackson.databind.node.ObjectNode>valueToTree(v)).toList();
                });
        org.mockito.Mockito.when(repository.findIntrinsicPage(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyInt()))
                .thenAnswer(invocation -> fixtures.findIntrinsicPage(invocation.getArgument(0), invocation.getArgument(1)));
        org.mockito.Mockito.when(repository.findIntrinsicByDid(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> fixtures.findIntrinsicByDid(invocation.getArgument(0)));
        org.mockito.Mockito.when(repository.findRegisteredPage(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyInt()))
                .thenAnswer(invocation -> fixtures.findRegisteredPage(invocation.getArgument(0), invocation.getArgument(1)));
        org.mockito.Mockito.when(repository.findRegisteredByDid(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> fixtures.findRegisteredByDid(invocation.getArgument(0)));
        org.mockito.Mockito.when(repository.findRegisteredByPartyId(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyInt()))
                .thenAnswer(invocation -> fixtures.findRegisteredByPartyId(invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2)));
    }

    @Test
    void exposesDiscoveryOnlyDescriptors() {
        for (String family : new String[] {"did-registries", "credential-registries", "credentials",
                "asset-registries", "assets"}) {
            given().get("/v1/" + family).then().statusCode(200)
                    .body("family", equalTo(family))
                    .body("apiVersion", equalTo("v1"))
                    .body("instanceId", equalTo("did-api-1"))
                    .body("status", equalTo("discovery-only"));
        }
    }

    @Test
    void listsIntrinsicDidsWithoutRegistration() {
        given().get("/v1/dids").then().statusCode(200)
                .body("items", hasSize(2))
                .body("items[0].did", equalTo("did:example:alice"))
                .body("items[0].didDocument.id", equalTo("did:example:alice"))
                .body("items[0].registration", org.hamcrest.Matchers.nullValue())
                .body("page", equalTo(0)).body("pageSize", equalTo(50)).body("hasNext", equalTo(false));
    }

    @Test
    void listsAndResolvesRegisteredDids() {
        given().get("/v1/registered-dids").then().statusCode(200)
                .body("items", hasSize(2))
                .body("items[0].did", equalTo("did:example:alice"))
                .body("items[0].registration.registryAdmin", equalTo("DidDemoAlice::1220-test"));
        given().urlEncodingEnabled(false).get("/v1/registered-dids/did%3Aexample%3Aalice").then()
                .statusCode(200).body("did", equalTo("did:example:alice"))
                .body("didDocument.id", equalTo("did:example:alice"));
    }

    @Test
    void emitsExplicitEmptyVerificationArrays() {
        for (String root : new String[] {"/v1/dids", "/v1/registered-dids"}) {
            for (boolean collection : new boolean[] {true, false}) {
                String path = collection ? root : root + "/did%3Aexample%3Aalice";
                String document = collection ? "items[0].didDocument" : "didDocument";
                var response = given().urlEncodingEnabled(false).get(path).then().statusCode(200)
                        .body(document + ".id", equalTo("did:example:alice"))
                        .body(document + ".controller", equalTo(java.util.List.of("did:example:alice")))
                        .body(document + ".verificationMethod", equalTo(java.util.List.of()));
                for (String relationship : new String[] {"authentication", "assertionMethod", "keyAgreement",
                        "capabilityInvocation", "capabilityDelegation"}) {
                    response.body(document + ".verificationRelationships." + relationship, equalTo(java.util.List.of()));
                }
            }
        }
    }

    @Test
    void usesSameStrictCodecForBothFamilies() {
        for (String root : new String[] {"/v1/dids/", "/v1/registered-dids/"}) {
            given().urlEncodingEnabled(false).get(root + "did%3Aexample%3Aalice").then().statusCode(200);
            for (String segment : new String[] {"did%3aexample%3aalice", "did:example:alice", "did%253Aexample%253Aalice", "did%3Aexample%3Aalice%2Fkey"}) {
                given().urlEncodingEnabled(false).get(root + segment).then().statusCode(400);
            }
        }
    }

    @Test
    void supportsRegisteredPartyFilterAndPagination() {
        given().queryParam("partyId", "DidDemoAlice::1220-test").get("/v1/registered-dids").then()
                .statusCode(200).body("items", hasSize(1));
        given().queryParam("pageSize", 1).get("/v1/dids").then()
                .statusCode(200).body("items", hasSize(1)).body("hasNext", equalTo(true));
        given().queryParam("pageSize", 101).get("/v1/dids").then().statusCode(400);
    }

    @Test
    void exposesSnapshotTokensAndCapabilitiesOverHttp() {
        String token = given().queryParam("pageSize", 1).queryParam("state", "all").get("/v1/dids").then()
                .statusCode(200).body("snapshotOffset", equalTo(123))
                .extract().path("nextPageToken");
        given().queryParam("nextPageToken", token).get("/v1/dids").then().statusCode(200).body("page", equalTo(1));
        given().queryParam("nextPageToken", token).queryParam("page", 0).get("/v1/dids").then().statusCode(200).body("page", equalTo(0));
        given().queryParam("nextPageToken", token).get("/v1/registered-dids").then()
                .statusCode(400).body("error", equalTo("token_wrong_resource"));
        given().queryParam("nextPageToken", token).queryParam("state", "archived").get("/v1/dids").then()
                .statusCode(400).body("error", equalTo("token_query_mismatch"));
        given().queryParam("nextPageToken", "broken").get("/v1/dids").then()
                .statusCode(400).body("error", equalTo("invalid_token"));
        given().queryParam("createdFrom", "not-a-time").get("/v1/dids").then().statusCode(400);
        given().get("/v1/dids/capabilities").then().statusCode(200)
                .body("listings.consistency", equalTo("single-pqs-snapshot"))
                .body("bft.singularOnly", equalTo(true)).body("bft.collections", equalTo(false));
    }

    @Test
    void bindsCanonicalTimeFiltersForBothCollections() {
        String from = "2026-01-01T00:00:00Z";
        String until = "2026-10-01T00:00:00Z";
        for (String root : new String[] {"/v1/dids", "/v1/registered-dids"}) {
            given().queryParam("state", "all").queryParam("createdFrom", from).queryParam("createdUntil", until)
                    .queryParam("archivedFrom", from).queryParam("archivedUntil", until).get(root).then().statusCode(200);
            org.mockito.Mockito.verify(query).findHistory(org.mockito.ArgumentMatchers.eq(root.endsWith("registered-dids")),
                    org.mockito.ArgumentMatchers.eq(new io.unlockit.application.did.manager.DidListingManager.Filters(
                            "all", null, from, until, from, until)), org.mockito.ArgumentMatchers.eq(123L),
                    org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull(),
                    org.mockito.ArgumentMatchers.eq(51), org.mockito.ArgumentMatchers.eq(0L));
            for (String prefix : new String[] {"created", "archived"}) {
                for (String invalidUntil : new String[] {from, "2025-01-01T00:00:00Z"}) {
                    given().queryParam(prefix + "From", from).queryParam(prefix + "Until", invalidUntil)
                            .get(root).then().statusCode(400).body("error", equalTo("invalid_request"));
                }
            }
        }
        given().get("/v1/dids/capabilities").then().statusCode(200)
                .body("listings.timeFilters", equalTo(java.util.List.of("createdFrom", "createdUntil", "archivedFrom", "archivedUntil")));
    }

    @Test
    void unknownDidsReturnNotFound() {
        given().urlEncodingEnabled(false).get("/v1/dids/did%3Aexample%3Aunknown").then().statusCode(404);
        given().urlEncodingEnabled(false).get("/v1/registered-dids/did%3Aexample%3Aunknown").then().statusCode(404);
    }

    @Test
    void malformedPercentEscapeIsRejectedByTheHttpServer() throws Exception {
        try (Socket socket = new Socket(baseUri.getHost(), baseUri.getPort());
                OutputStreamWriter writer = new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.US_ASCII);
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII))) {
            writer.write("GET /v1/dids/did%ZZexample HTTP/1.1\r\nHost: " + baseUri.getHost() + "\r\nConnection: close\r\n\r\n");
            writer.flush();
            org.junit.jupiter.api.Assertions.assertTrue(reader.readLine().contains(" 400 "));
        }
    }
}
