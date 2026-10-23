package io.unlockit.infrastructure.credential.pqs;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.unlockit.application.credential.mapper.CredentialResponseMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class OfflineCredentialVerifierTest {
  private final ObjectMapper json = new ObjectMapper();
  private final PqsCredentialQueryClient pqs = new PqsCredentialQueryClient(null, json);
  private final CredentialResponseMapper mapper = new CredentialResponseMapper(
      Clock.fixed(Instant.parse("2027-01-01T00:00:00Z"), ZoneOffset.UTC));
  private Path fixture(String name) { return Path.of("../fixtures", name); }

  private JsonNode payload(JsonNode document, String securing) throws Exception {
    JsonNode source = document;
    var result = json.createObjectNode();
    result.put("id", source.path("id").asText());
    result.set("issuer", json.createObjectNode().put("tag", "W3C_VC_Identifier").put("value", source.path("issuer").asText()));
    result.set("credentialTypes", source.path("type"));
    var subjects = json.createArrayNode();
    for (JsonNode subject : source.path("credentialSubject")) {
      var projected = json.createObjectNode();
      if (subject.has("id")) projected.set("id", json.createObjectNode().put("tag", "W3C_VC_Identifier").put("value", subject.path("id").asText()));
      var claims = json.createObjectNode();
      subject.fields().forEachRemaining(field -> {
        if (!field.getKey().equals("id")) claims.set(field.getKey(), any(field.getValue()));
      });
      projected.set("claims", claims);
      subjects.add(projected);
    }
    result.set("credentialSubject", subjects);
    result.set("credentialSchema", json.createArrayNode().add(json.createObjectNode()
        .put("id", source.at("/credentialSchema/0/id").asText())
        .put("schemaType", "JsonSchema").put("digestSRI", source.at("/credentialSchema/0/digestSRI").asText())));
    result.put("validFrom", source.path("validFrom").asText());
    result.put("validUntil", source.path("validUntil").asText());
    result.set("holders", json.createArrayNode());
    result.set("anchorers", json.createObjectNode().put("hd", "anchor").set("tl", json.createArrayNode()));
    result.set("securing", json.readTree(securing));
    return result;
  }

  private JsonNode any(JsonNode value) {
    var result = json.createObjectNode();
    if (value.isObject()) {
      var entries = json.createObjectNode();
      value.fields().forEachRemaining(field -> entries.set(field.getKey(), any(field.getValue())));
      result.put("tag", "AV_Map").set("value", entries);
    } else if (value.isArray()) {
      var entries = json.createArrayNode();
      value.forEach(entry -> entries.add(any(entry)));
      result.put("tag", "AV_List").set("value", entries);
    } else if (value.isNumber()) result.put("tag", "AV_Int").put("value", value.asText());
    else result.put("tag", "AV_Text").set("value", value);
    return result;
  }

  private JsonNode envelope(String tag, String name, String content) {
    var wrapper = json.createObjectNode();
    wrapper.put("tag", tag);
    wrapper.set("value", json.createObjectNode().put(name, content));
    return wrapper;
  }

  private JsonNode mapped(JsonNode payload) throws Exception {
    return json.valueToTree(mapper.toResponse(pqs.mapCredential("cid", payload.toString())));
  }

  @Test void jwtVerifiesAndPreservesExactCompactBytes() throws Exception {
    String jwt = Files.readString(fixture("external-did.jwt")).trim();
    JsonNode source = json.readTree(java.util.Base64.getUrlDecoder().decode(jwt.split("\\.")[1]));
    var wrapping = (com.fasterxml.jackson.databind.node.ObjectNode) envelope("EnvelopedProof", "encoded", jwt);
    ((com.fasterxml.jackson.databind.node.ObjectNode) wrapping.path("value")).put("mediaType", "application/vc+jwt");
    JsonNode response = mapped(payload(source, wrapping.toString()));
    assertEquals("VERIFIED", response.at("/verification/status").asText(), response.toString());
    assertEquals("Success", response.at("/verification/schemaValidation/status").asText());
    assertEquals("data:application/vc+jwt," + jwt, response.at("/credential/id").asText());
    assertEquals("EnvelopedVerifiableCredential", response.at("/credential/type").asText());
    assertEquals(source.path("issuer").asText(), response.at("/verification/controller").asText());
    var changed = (com.fasterxml.jackson.databind.node.ObjectNode) payload(source, wrapping.toString());
    changed.put("id", "urn:wrong");
    assertEquals("FAILED", mapped(changed).at("/verification/status").asText());
  }

  @Test void unsupportedPlaceholderAndSchemaDigestRemainIndependent() throws Exception {
    String jwt = Files.readString(fixture("external-did.jwt")).trim();
    JsonNode source = json.readTree(java.util.Base64.getUrlDecoder().decode(jwt.split("\\.")[1]));
    var wrapping = (com.fasterxml.jackson.databind.node.ObjectNode) envelope("EnvelopedProof", "encoded", "not-a-jws");
    ((com.fasterxml.jackson.databind.node.ObjectNode) wrapping.path("value")).put("mediaType", "application/vc+sd-jwt");
    var input = (com.fasterxml.jackson.databind.node.ObjectNode) payload(source, wrapping.toString());
    JsonNode unsupported = mapped(input);
    assertEquals("INDETERMINATE", unsupported.at("/verification/status").asText());
    var placeholder = (com.fasterxml.jackson.databind.node.ObjectNode) envelope("EnvelopedProof", "encoded", "placeholder.jws.scenario-base-credential");
    ((com.fasterxml.jackson.databind.node.ObjectNode) placeholder.path("value")).put("mediaType", "application/vc+jwt");
    JsonNode structural = mapped(payload(source, placeholder.toString()));
    assertEquals("INDETERMINATE", structural.at("/verification/status").asText());
    assertEquals("Indeterminate", structural.at("/verification/schemaValidation/status").asText());
    assertEquals("Success", unsupported.at("/verification/schemaValidation/status").asText());
    ((com.fasterxml.jackson.databind.node.ObjectNode) input.at("/credentialSchema/0")).put("digestSRI", "sha256-wrong");
    assertEquals("Failure", mapped(input).at("/verification/schemaValidation/status").asText());
    ((com.fasterxml.jackson.databind.node.ObjectNode) input.at("/securing/value")).put("mediaType", "application/vc+jwt");
    assertEquals("FAILED", mapped(input).at("/verification/status").asText());
  }

  @Test void jcsCanonicalizesRfc8785NumberAndUtf16KeyOrdering() throws Exception {
    var canonicalizer = new org.erdtman.jcs.JsonCanonicalizer(
        "{\"\\uE000\":1.0,\"\\uD800\\uDC00\":1e-7,\"a\":1e30}");
    assertEquals("{\"a\":1e+30,\"𐀀\":1e-7,\"\":1}", canonicalizer.getEncodedString());
  }

  @Test void validityAndIssuerBindingAreCheckedAtFixedTime() throws Exception {
    String jwt = Files.readString(fixture("external-did.jwt")).trim();
    JsonNode source = json.readTree(java.util.Base64.getUrlDecoder().decode(jwt.split("\\.")[1]));
    var wrapping = (com.fasterxml.jackson.databind.node.ObjectNode) envelope("EnvelopedProof", "encoded", jwt);
    ((com.fasterxml.jackson.databind.node.ObjectNode) wrapping.path("value")).put("mediaType", "application/vc+jwt");
    JsonNode payload = payload(source, wrapping.toString());
    var expired = new CredentialResponseMapper(Clock.fixed(Instant.parse("2031-01-01T00:00:00Z"), ZoneOffset.UTC));
    assertEquals("FAILED", expired.toResponse(pqs.mapCredential("cid", payload.toString())).verification().status());
    ((com.fasterxml.jackson.databind.node.ObjectNode) payload).set("issuer", json.createObjectNode()
        .put("tag", "W3C_VC_Identifier").put("value", "did:key:zOther"));
    assertEquals("FAILED", mapped(payload).at("/verification/status").asText());
  }

  @Test void embeddedVerifiesAndTamperingFailsWhilePreservingOriginalDocument() throws Exception {
    String original = Files.readString(fixture("external-holderless.json")).trim();
    JsonNode source = json.readTree(original);
    String summary = json.createObjectNode().put("proofType", "DataIntegrityProof")
        .put("cryptosuite", "eddsa-jcs-2022").put("created", "2026-09-18T00:00:00Z")
        .put("verificationMethod", source.at("/proof/verificationMethod").asText())
        .put("proofPurpose", "assertionMethod").toString();
    for (String name : new String[] {"external-holderless.json", "external-holderless-tampered.json", "external-tampered.json"}) {
      String secured = Files.readString(fixture(name)).trim();
      var wrapping = (com.fasterxml.jackson.databind.node.ObjectNode) envelope("EmbeddedProof", "securedDocument", secured);
      var proofs = json.createObjectNode();
      proofs.set("hd", json.readTree(summary));
      proofs.set("tl", json.createArrayNode());
      ((com.fasterxml.jackson.databind.node.ObjectNode) wrapping.path("value")).set("proofs", proofs);
      String securing = wrapping.toString();
      JsonNode projected = name.equals("external-holderless-tampered.json") ? source : json.readTree(secured);
      JsonNode response = mapped(payload(projected, securing));
      assertEquals(name.contains("tampered") ? "FAILED" : "VERIFIED", response.at("/verification/status").asText(), response.toString());
      if (name.equals("external-tampered.json")) {
        assertEquals("urn:demo:scenario:external-tampered", response.at("/credentialId").asText());
        assertTrue(response.at("/verification/errors").toString().contains("Invalid Data Integrity signature"));
        assertFalse(response.at("/verification/errors").toString().contains("Projection mismatch"));
      }
      assertEquals(json.readTree(secured), response.path("credential"));
      assertEquals("Success", response.at("/verification/schemaValidation/status").asText());
    }
  }
}
