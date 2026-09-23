package io.unlockit.infrastructure.credential.pqs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.agroal.api.AgroalDataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import org.junit.jupiter.api.Test;

class PqsCredentialQueryClientTest {
  private static final String CREDENTIAL = """
      {"id":"urn:demo:credential:001","issuer":{"tag":"W3C_VC_Identifier_Party","value":"issuer"},"credentialTypes":["VerifiableCredential"],"credentialSubject":[{"id":null,"claims":{}}],"holders":["holder"],"validFrom":"2026-09-18T00:00:00Z","validUntil":"2026-12-17T00:00:00Z"}
      """;
  private static final String REGISTRATION = """
      {"registryAdmin":"admin","registeredAt":"2026-09-18T00:00:00Z","expiresAt":"2026-12-17T00:00:00Z","meta":{},"internal":"excluded"}
      """;

  @Test
  void mapsIntrinsicAndRegisteredPayloadsSeparately() throws Exception {
    var client = new PqsCredentialQueryClient(null, new ObjectMapper());
    var credential = client.mapCredential("cid", CREDENTIAL);
    assertEquals("cid", credential.contractId());
    assertEquals("urn:demo:credential:001", credential.credentialId());
    assertEquals("VerifiableCredential", credential.credentialTypes().get(0).asText());
    assertEquals("2026-12-17T00:00:00Z", credential.validUntil());

    var registered = client.mapRegisteredPayloads("cid", CREDENTIAL, REGISTRATION);
    assertEquals("admin", registered.registration().registryAdmin());
    assertEquals("2026-12-17T00:00:00Z", registered.registration().expiresAt());
    assertEquals("cid", registered.credential().contractId());
  }

  @Test
  void bindsIntrinsicExactLookupWithoutRegistrationJoin() throws Exception {
    AgroalDataSource dataSource = mock(AgroalDataSource.class);
    Connection connection = mock(Connection.class);
    PreparedStatement statement = mock(PreparedStatement.class);
    ResultSet rows = mock(ResultSet.class);
    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.prepareStatement(PqsCredentialQueryClient.FIND_CREDENTIAL_BY_ID_SQL)).thenReturn(statement);
    when(statement.executeQuery()).thenReturn(rows);
    when(rows.next()).thenReturn(false);

    new PqsCredentialQueryClient(dataSource, new ObjectMapper()).findCredentialById("credential");

    verify(statement).setString(1, PqsCredentialQueryClient.CREDENTIAL_INTERFACE);
    verify(statement).setString(2, "credential");
  }

  @Test
  void bindsRegisteredPageJoinAndBounds() throws Exception {
    AgroalDataSource dataSource = mock(AgroalDataSource.class);
    Connection connection = mock(Connection.class);
    PreparedStatement statement = mock(PreparedStatement.class);
    ResultSet rows = mock(ResultSet.class);
    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.prepareStatement(PqsCredentialQueryClient.FIND_REGISTERED_PAGE_SQL)).thenReturn(statement);
    when(statement.executeQuery()).thenReturn(rows);
    when(rows.next()).thenReturn(false);

    new PqsCredentialQueryClient(dataSource, new ObjectMapper()).findRegisteredCredentialPage(200, 101);

    verify(statement).setString(1, PqsCredentialQueryClient.CREDENTIAL_INTERFACE);
    verify(statement).setString(2, PqsCredentialQueryClient.REGISTERED_CREDENTIAL_INTERFACE);
    verify(statement).setInt(3, 101);
    verify(statement).setLong(4, 200);
  }

  @Test
  void queriesUseDocumentedPqsFunctionsAndDeterministicOrder() {
    assertTrue(PqsCredentialQueryClient.FIND_CREDENTIAL_BY_ID_SQL.contains("active(?)"));
    assertFalse(PqsCredentialQueryClient.FIND_CREDENTIAL_BY_ID_SQL.contains("join"));
    assertTrue(PqsCredentialQueryClient.FIND_REGISTERED_BY_ID_SQL.contains("join active(?)"));
    assertTrue(PqsCredentialQueryClient.FIND_REGISTERED_PAGE_SQL.contains("c.contract_id"));
    assertTrue(PqsCredentialQueryClient.FIND_REGISTERED_PAGE_SQL.contains("limit ? offset ?"));
    assertFalse(PqsCredentialQueryClient.FIND_REGISTERED_PAGE_SQL.contains("__"));
  }
}
