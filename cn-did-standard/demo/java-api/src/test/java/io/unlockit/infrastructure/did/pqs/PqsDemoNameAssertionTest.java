package io.unlockit.infrastructure.did.pqs;

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

class PqsDemoNameAssertionTest {
  @Test
  void aliasLookupIsBoundedAndNeverScansTheCollection() {
    String sql = PqsDidQueryClient.FIND_DEMO_NAME_ASSERTIONS_SQL;
    assertTrue(sql.contains("alias ->> 'alias' = ?::text"));
    assertTrue(sql.contains("alias ->> 'subject' = ?::text"));
    assertTrue(sql.contains("limit ?"));
    assertFalse(sql.contains("offset"));
  }

  @Test
  void bindsAliasSubjectAndLimit() throws Exception {
    AgroalDataSource source = mock(AgroalDataSource.class);
    Connection connection = mock(Connection.class);
    PreparedStatement statement = mock(PreparedStatement.class);
    ResultSet rows = mock(ResultSet.class);
    when(source.getConnection()).thenReturn(connection);
    when(connection.prepareStatement(PqsDidQueryClient.FIND_DEMO_NAME_ASSERTIONS_SQL)).thenReturn(statement);
    when(statement.executeQuery()).thenReturn(rows);
    when(rows.next()).thenReturn(true, false);
    when(rows.getString("did")).thenReturn("did:canton:DSO::test");
    when(rows.getString("alias")).thenReturn("urn:fqpn:localnet:demo:dso.canton");
    when(rows.getString("subject")).thenReturn("DSO::test");
    var client = new PqsDidQueryClient(source, new ObjectMapper());
    var matches = client.findDemoNameAssertions("urn:fqpn:localnet:demo:dso.canton", null, 10);
    assertEquals(1, matches.size());
    assertEquals("DSO::test", matches.getFirst().subject());
    verify(statement).setString(1, PqsDidQueryClient.DEMO_DID_DOCUMENT_TEMPLATE);
    verify(statement).setString(2, "urn:fqpn:localnet:demo:dso.canton");
    verify(statement).setString(3, "urn:fqpn:localnet:demo:dso.canton");
    verify(statement).setString(4, null);
    verify(statement).setString(5, null);
    verify(statement).setInt(6, 10);
  }
}
