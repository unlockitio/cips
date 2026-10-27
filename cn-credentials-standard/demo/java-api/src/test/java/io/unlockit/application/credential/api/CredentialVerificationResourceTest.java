package io.unlockit.application.credential.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

@QuarkusTest
class CredentialVerificationResourceTest {
  private static byte[] fixture(String name) throws Exception {
    return Files.readAllBytes(Path.of("../fixtures", name));
  }

  @Test void evaluatesExternalJwtWithoutPqsOrCredentialId() throws Exception {
    byte[] jwt = new String(fixture("external-did.jwt"), StandardCharsets.UTF_8).trim().getBytes(StandardCharsets.UTF_8);
    var result = given().contentType("application/vc+jwt").body(jwt).post("/v1/credential-verifications")
        .then().statusCode(200).body("status", equalTo("VERIFIED"))
        .body("schemaValidation.status", equalTo("Success")).extract().asString();
    var json = new ObjectMapper().readTree(result);
    assertFalse(json.has("credentialId"));
    assertFalse(json.has("registration"));
    assertFalse(json.path("verifiedAt").asText().isBlank());
    assertEquals("VERIFIED", given().contentType("application/vc+jwt").body(jwt)
        .post("/v1/credential-verifications").then().statusCode(200).extract().path("status"));
  }

  @Test void evaluatesEmbeddedTamperingAndUnsupportedProofsSeparatelyFromSchema() throws Exception {
    given().contentType("application/vc").body(fixture("external-holderless.json"))
        .post("/v1/credential-verifications").then().statusCode(200)
        .body("status", equalTo("VERIFIED"), "schemaValidation.status", equalTo("Success"));
    given().contentType("application/vc").body(fixture("external-tampered.json"))
        .post("/v1/credential-verifications").then().statusCode(200)
        .body("status", equalTo("FAILED"), "schemaValidation.status", equalTo("Success"));
    for (String type : new String[] {"application/vc+sd-jwt", "application/vc+cose"}) {
      given().contentType(type).body("opaque-proof".getBytes(StandardCharsets.UTF_8))
          .post("/v1/credential-verifications").then().statusCode(200)
          .body("status", equalTo("INDETERMINATE"), "schemaValidation.status", equalTo("Indeterminate"));
    }
    given().contentType("application/vc").body("{".getBytes(StandardCharsets.UTF_8))
        .post("/v1/credential-verifications").then().statusCode(200)
        .body("status", equalTo("FAILED"));
  }
}
