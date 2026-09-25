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
      {"id":"urn:demo:credential:001","issuer":{"tag":"W3C_VC_Identifier_Party","value":"issuer"},"credentialTypes":["VerifiableCredential"],"credentialSubject":[{"id":null,"claims":{}}],"holders":[],"anchorers":{"hd":"anchor","tl":[]},"validFrom":"2026-09-18T00:00:00Z","validUntil":"2026-12-17T00:00:00Z"}
      """;
  private static final String REGISTRATION = """
      {"registryAdmin":"admin","registeredAt":"2026-09-18T00:00:00Z","expiresAt":"2026-12-17T00:00:00Z","meta":{},"internal":"excluded"}
      """;
  private static final String FACTORY = """
      {"registryAdmin":"admin","anchorers":{"hd":"anchor","tl":[]}}
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
  void preservesExternalIssuerAndHolderlessRolesThroughResponseMapping() throws Exception {
    var client = new PqsCredentialQueryClient(null, new ObjectMapper());
    var payload = CREDENTIAL.replace("W3C_VC_Identifier_Party", "W3C_VC_Identifier")
        .replace("\"value\":\"issuer\"", "\"value\":\"https://external.example/issuer\"");
    var registered = client.mapRegisteredPayloads("cid", payload, REGISTRATION);
    var mapper = new io.unlockit.application.credential.mapper.CredentialResponseMapper();
    var response = mapper.toResponse(registered);
    var json = new ObjectMapper().valueToTree(response);
    assertEquals("W3C_VC_Identifier", json.at("/credential/issuer/tag").asText());
    assertEquals("https://external.example/issuer", json.at("/credential/issuer/value").asText());
    assertTrue(json.at("/credential/holders").isArray());
    assertEquals(0, json.at("/credential/holders").size());
    assertEquals("anchor", json.at("/credential/anchorers/hd").asText());
    assertEquals(0, json.at("/credential/anchorers/tl").size());
    assertEquals("active", json.path("lifecycleState").asText());
    assertEquals("2026-09-18T00:00:00Z", json.at("/registration/registeredAt").asText());
    assertEquals(mapper.toResponse(registered.credential()).credential(), response.credential());
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
  void mapsAndQueriesLogicalRegistriesThroughFactoryProjection() throws Exception {
    var mapper = new PqsCredentialQueryClient(null, new ObjectMapper());
    var factory = mapper.mapCredentialRegistryFactory("factory-contract", FACTORY);
    assertEquals("admin", factory.registryAdmin());
    assertEquals("anchor", factory.anchorers().path("hd").asText());

    AgroalDataSource dataSource = mock(AgroalDataSource.class);
    Connection connection = mock(Connection.class);
    PreparedStatement statement = mock(PreparedStatement.class);
    ResultSet rows = mock(ResultSet.class);
    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.prepareStatement(PqsCredentialQueryClient.FIND_CREDENTIAL_REGISTRY_IDS_SQL))
        .thenReturn(statement);
    when(statement.executeQuery()).thenReturn(rows);
    when(rows.next()).thenReturn(false);

    new PqsCredentialQueryClient(dataSource, new ObjectMapper())
        .findCredentialRegistryIds(100, 51);

    verify(statement).setString(1, PqsCredentialQueryClient.CREDENTIAL_REGISTRY_FACTORY_INTERFACE);
    verify(statement).setInt(2, 51);
    verify(statement).setLong(3, 100);
    assertTrue(PqsCredentialQueryClient.FIND_CREDENTIAL_REGISTRY_IDS_SQL.contains("group by"));
    assertTrue(PqsCredentialQueryClient.FIND_CREDENTIAL_REGISTRY_IDS_SQL.contains("registryAdmin"));
    assertTrue(PqsCredentialQueryClient.FIND_CREDENTIAL_REGISTRY_FACTORIES_SQL.contains("order by f.contract_id"));
  }

  @Test
  void historySqlUsesSnapshotFunctionsCompatibleEventsAndAllFilters() {
    assertEquals("active(?::text, ?::bigint)", PqsCredentialQueryClient.historySource("active"));
    assertEquals("archives(?::text, 0::bigint, ?::bigint)", PqsCredentialQueryClient.historySource("archived"));
    assertEquals("creates(?::text, 0::bigint, ?::bigint)", PqsCredentialQueryClient.historySource("all"));
    for (String state : java.util.List.of("active", "archived", "all")) {
      String sql = PqsCredentialQueryClient.historySql(true, state, false);
      for (String fragment : java.util.List.of("c.create_event_id = r.create_event_id",
          "c.created_at_offset = r.created_at_offset", "c.created_at_ix = r.created_at_ix",
          "c.created_effective_at >= ?::timestamptz", "c.created_effective_at < ?::timestamptz",
          "a.archived_effective_at >= ?::timestamptz", "a.archived_effective_at < ?::timestamptz",
          "issuer' ->> 'value'", "holders' @> jsonb_build_array", "s -> 'id' ->> 'value'",
          "starts_with(k, ?::text)", "r.payload ->> 'registryAdmin'", "limit ? offset ?"))
        assertTrue(sql.contains(fragment), fragment);
      assertFalse(sql.contains("__"));
    }
    assertTrue(PqsCredentialQueryClient.historySql(true, "archived", false).contains("c.archive_event_id = r.archive_event_id"));
    assertTrue(PqsCredentialQueryClient.historySql(false, "all", true).contains("c.created_at_offset desc, c.created_at_ix desc"));
    assertFalse(PqsCredentialQueryClient.historySql(false, "active", false).contains("join registrations"));
  }

  @Test
  void allRegisteredSqlRequiresCompatibleArchivesAndAllowsActiveNulls() throws Exception {
    String compatible = "((c.archive_event_id is null and r.archive_event_id is null"
        + " and c.archived_at_offset is null and r.archived_at_offset is null"
        + " and c.archived_at_ix is null and r.archived_at_ix is null)"
        + " or (c.archive_event_id = r.archive_event_id"
        + " and c.archived_at_offset = r.archived_at_offset and c.archived_at_ix = r.archived_at_ix))";
    for (boolean singular : java.util.List.of(false, true)) {
      String sql = PqsCredentialQueryClient.historySql(true, "all", singular);
      assertTrue(sql.contains(" and " + compatible + " where"));
      String archived = PqsCredentialQueryClient.historySql(true, "archived", singular);
      assertTrue(archived.contains(" and c.archive_event_id = r.archive_event_id"
          + " and c.archived_at_offset = r.archived_at_offset and c.archived_at_ix = r.archived_at_ix where"));
      assertFalse(archived.contains("c.archive_event_id is null"));
    }
    String sql = PqsCredentialQueryClient.historySql(true, "all", false);
    String predicate = sql.substring(sql.indexOf(" and ((c.archive_event_id") + 5, sql.indexOf(" where"));
    try (var connection = java.sql.DriverManager.getConnection("jdbc:h2:mem:archiveCompatibility")) {
      String cases = "(values (1, null, null, null, null, null, null),"
          + " (2, 'event', 42, 3, 'event', 42, 3),"
          + " (3, 'event', 42, 3, 'other', 42, 3),"
          + " (4, 'event', 42, 3, 'event', 43, 3),"
          + " (5, 'event', 42, 3, 'event', 42, 4),"
          + " (6, null, null, null, 'event', 42, 3),"
          + " (7, 'event', 42, 3, null, null, null),"
          + " (8, null, 42, 3, null, 42, 3))"
          + " cases(id, ce, co, ci, re, ro, ri)";
      try (var rows = connection.createStatement().executeQuery("with cases as (select * from " + cases + ")"
          + " select c.id from (select id, ce archive_event_id, co archived_at_offset, ci archived_at_ix from cases) c"
          + " join (select id, re archive_event_id, ro archived_at_offset, ri archived_at_ix from cases) r"
          + " on c.id = r.id and " + predicate + " order by c.id")) {
        assertTrue(rows.next());
        assertEquals(1, rows.getInt(1));
        assertTrue(rows.next());
        assertEquals(2, rows.getInt(1));
        assertFalse(rows.next());
      }
    }
  }

  @Test
  void snapshotUsesBigintAndMapsUnavailableOffset() throws Exception {
    var source = mock(AgroalDataSource.class);
    var connection = mock(Connection.class);
    var statement = mock(PreparedStatement.class);
    var rows = mock(ResultSet.class);
    when(source.getConnection()).thenReturn(connection);
    when(connection.prepareStatement("select set_latest(NULL)")).thenReturn(statement);
    when(statement.executeQuery()).thenReturn(rows);
    when(rows.next()).thenReturn(true);
    when(rows.getLong(1)).thenReturn(9007199254740993L);
    var client = new PqsCredentialQueryClient(source, new ObjectMapper());
    assertEquals(9007199254740993L, client.latestOffset());
    when(connection.prepareStatement("select validate_offset_exists(?::bigint)")).thenReturn(statement);
    client.validateOffset(9007199254740993L);
    verify(statement).setLong(1, 9007199254740993L);
    when(statement.executeQuery()).thenThrow(new java.sql.SQLException("pruned", "P0001"));
    var failure = org.junit.jupiter.api.Assertions.assertThrows(
        io.unlockit.domain.credential.exception.InvalidPaginationException.class, () -> client.validateOffset(12));
    assertEquals("snapshot_unavailable", failure.getMessage());
  }

  @Test
  void mapsLifecycleUsingJdbcCompositeTextAndNullableBigints() throws Exception {
    try (var connection = java.sql.DriverManager.getConnection("jdbc:h2:mem:lifecycle")) {
      var rows = connection.createStatement().executeQuery("select 'pkg:Mod:T' template_fqn, 'interface' payload_type,"
          + " 1 create_event_pk, '(9007199254740993,2)' create_event_id, 2 created_at_ix,"
          + " 9007199254740993 created_at_offset, timestamp '2026-01-01 00:00:00' created_effective_at,"
          + " null archive_event_pk, null archive_event_id, null archived_at_ix, null archived_at_offset,"
          + " null archived_effective_at");
      assertTrue(rows.next());
      var value = PqsCredentialQueryClient.mapLifecycle(rows);
      assertEquals("active", value.state());
      assertEquals("(9007199254740993,2)", value.createEventId());
      assertEquals(9007199254740993L, value.createdAtOffset());
      org.junit.jupiter.api.Assertions.assertNull(value.archivedAtOffset());
      org.junit.jupiter.api.Assertions.assertNull(value.archivedEffectiveAt());
    }
  }

  @Test
  void historyBindsBothInterfacesSnapshotCursorFiltersAndBounds() throws Exception {
    var source = mock(AgroalDataSource.class);
    var connection = mock(Connection.class);
    var statement = mock(PreparedStatement.class);
    var rows = mock(ResultSet.class);
    when(source.getConnection()).thenReturn(connection);
    when(connection.prepareStatement(PqsCredentialQueryClient.historySql(true, "all", false))).thenReturn(statement);
    when(statement.executeQuery()).thenReturn(rows);
    var filters = new io.unlockit.domain.credential.query.CredentialFilters("all", "2026-01-01T00:00:00Z",
        "2026-01-02T00:00:00Z", null, null, "issuer", "holder", "subject", "prefix%_", "admin");
    new PqsCredentialQueryClient(source, new ObjectMapper()).findRegisteredCredentialHistory(filters, 42, "id", "cid", null, 51, 100);
    verify(statement).setString(5, PqsCredentialQueryClient.REGISTERED_CREDENTIAL_INTERFACE);
    verify(statement).setLong(6, 42);
    verify(statement).setString(7, "id");
    verify(statement).setString(9, "cid");
    verify(statement).setString(26, "prefix%_");
    verify(statement).setString(28, "admin");
    verify(statement).setInt(30, 51);
    verify(statement).setLong(31, 100);
  }

  @Test
  void effectiveTimeBoundariesAndArchiveNullsUseHalfOpenRanges() throws Exception {
    try (var connection = java.sql.DriverManager.getConnection("jdbc:h2:mem:boundaries")) {
      String sql = "select id from (values (1, timestamp '2026-01-01 00:00:00', timestamp '2026-02-01 00:00:00'),"
          + " (2, timestamp '2026-01-02 00:00:00', timestamp '2026-02-02 00:00:00'),"
          + " (3, timestamp '2026-01-01 00:00:00', cast(null as timestamp))) c(id, created_effective_at, archived_effective_at)"
          + " where c.created_effective_at >= ? and c.created_effective_at < ?"
          + " and c.archived_effective_at >= ? and c.archived_effective_at < ? order by id";
      try (var statement = connection.prepareStatement(sql)) {
        statement.setTimestamp(1, java.sql.Timestamp.valueOf("2026-01-01 00:00:00"));
        statement.setTimestamp(2, java.sql.Timestamp.valueOf("2026-01-02 00:00:00"));
        statement.setTimestamp(3, java.sql.Timestamp.valueOf("2026-02-01 00:00:00"));
        statement.setTimestamp(4, java.sql.Timestamp.valueOf("2026-02-02 00:00:00"));
        try (var rows = statement.executeQuery()) {
          assertTrue(rows.next());
          assertEquals(1, rows.getInt(1));
          assertFalse(rows.next());
        }
      }
    }
  }

  @Test
  void readinessExercisesSnapshotAndBothFamiliesForEveryState() {
    var client = org.mockito.Mockito.spy(new PqsCredentialQueryClient(null, new ObjectMapper()));
    org.mockito.Mockito.doReturn(42L).when(client).latestOffset();
    org.mockito.Mockito.doNothing().when(client).validateOffset(42);
    org.mockito.Mockito.doReturn(java.util.List.of()).when(client).findCredentialHistory(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(42L), org.mockito.ArgumentMatchers.isNull(),
        org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.eq(1), org.mockito.ArgumentMatchers.eq(0L));
    org.mockito.Mockito.doThrow(new io.unlockit.domain.credential.exception.PqsUnavailableException(new java.sql.SQLException("unsupported archive column")))
        .when(client).findRegisteredCredentialHistory(
            org.mockito.ArgumentMatchers.argThat(f -> f.state().equals("archived")), org.mockito.ArgumentMatchers.eq(42L),
            org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull(),
            org.mockito.ArgumentMatchers.eq(1), org.mockito.ArgumentMatchers.eq(0L));
    org.mockito.Mockito.doReturn(java.util.List.of()).when(client).findRegisteredCredentialHistory(
        org.mockito.ArgumentMatchers.argThat(f -> f.state().equals("active")), org.mockito.ArgumentMatchers.eq(42L),
        org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull(),
        org.mockito.ArgumentMatchers.eq(1), org.mockito.ArgumentMatchers.eq(0L));
    org.junit.jupiter.api.Assertions.assertThrows(io.unlockit.domain.credential.exception.PqsUnavailableException.class,
        client::checkCredentialProjections);
    verify(client).validateOffset(42);
    verify(client).findCredentialHistory(new io.unlockit.domain.credential.query.CredentialFilters("archived", null, null, null, null,
        null, null, null, null, null), 42, null, null, null, 1, 0);
  }

  @Test
  void queriesUseDocumentedPqsFunctionsAndDeterministicOrder() {
    assertTrue(PqsCredentialQueryClient.FIND_CREDENTIAL_BY_ID_SQL.contains("active(?)"));
    assertFalse(PqsCredentialQueryClient.FIND_CREDENTIAL_BY_ID_SQL.contains("join"));
    assertTrue(PqsCredentialQueryClient.FIND_REGISTERED_BY_ID_SQL.contains("join active(?)"));
    assertTrue(PqsCredentialQueryClient.FIND_REGISTERED_PAGE_SQL.contains("c.contract_id"));
    assertTrue(PqsCredentialQueryClient.FIND_REGISTERED_PAGE_SQL.contains("limit ? offset ?"));
    assertFalse(PqsCredentialQueryClient.FIND_REGISTERED_PAGE_SQL.contains("__"));
    assertTrue(PqsCredentialQueryClient.FIND_CREDENTIAL_REGISTRY_IDS_SQL.contains("active(?)"));
    assertTrue(PqsCredentialQueryClient.FIND_CREDENTIAL_REGISTRY_FACTORIES_SQL.contains("active(?)"));
    assertFalse(PqsCredentialQueryClient.FIND_CREDENTIAL_REGISTRY_IDS_SQL.contains("__"));
    assertFalse(PqsCredentialQueryClient.FIND_CREDENTIAL_REGISTRY_FACTORIES_SQL.contains("__"));
  }
}
