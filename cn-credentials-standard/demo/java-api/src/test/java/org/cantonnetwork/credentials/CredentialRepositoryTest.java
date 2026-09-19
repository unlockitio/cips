package org.cantonnetwork.credentials;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class CredentialRepositoryTest {
  @Test
  void mapsOnlyDeliberateApiFields() throws Exception {
    var mapper = new ObjectMapper();
    var credential = mapper.readTree("""
        {"id":"urn:demo:credential:001","issuer":{"tag":"W3C_VC_Identifier_Party","value":"issuer"},"credentialTypes":["VerifiableCredential"],"credentialSubject":[{"id":null,"claims":{}}],"holders":["holder"],"validFrom":"2026-09-18T00:00:00Z","validUntil":null}
        """);
    var registered = mapper.readTree("""
        {"registration":{"registryAdmin":"admin"},"internal":"excluded"}
        """);
    CredentialResponse response = CredentialRepository.map("cid", credential, registered);
    assertEquals("cid", response.contractId());
    assertEquals("urn:demo:credential:001", response.credentialId());
    assertEquals("W3C_VC_Identifier_Party", response.issuer().path("tag").asText());
    assertEquals("issuer", response.issuer().path("value").asText());
    assertEquals(1, response.subjects().size());
    assertEquals("admin", response.registryAdmin());
  }
}
