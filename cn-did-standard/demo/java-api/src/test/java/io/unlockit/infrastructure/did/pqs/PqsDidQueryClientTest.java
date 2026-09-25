package io.unlockit.infrastructure.did.pqs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

class PqsDidQueryClientTest {
    private static final String DOCUMENT = """
            {"id":{"value":"did:example:alice"},"services":[{"id":{"value":"did:example:alice#dids"},"serviceType":{"value":"CantonDidResolutionService"},"serviceEndpoint":{"version":"1.0","endpoints":{"hd":{"uri":{"value":"http://localhost:42003/v1/dids"},"priority":"0"},"tl":[{"uri":{"value":"http://localhost:42004/v1/dids"},"priority":"1"},{"uri":{"value":"http://localhost:42005/v1/dids"},"priority":"2"},{"uri":{"value":"http://localhost:42006/v1/dids"},"priority":"3"}]}}}],"controllers":{"hd":{"value":"did:example:alice"},"tl":[]},"verificationMethods":[],"verificationRelationships":{"authentication":[],"assertionMethod":[],"capabilityInvocation":[]}}
            """;
    private static final String REGISTERED = """
            {"document":%s,"registration":{"registryAdmin":"DidDemoAlice::1220-test","registeredAt":"2026-09-21T00:00:00Z","updatedAt":"2026-09-21T00:00:00Z","versionId":"demo-version-1","deactivated":false,"deactivatedAt":null,"source":"canton-ledger-pqs-demo","integrityEvidence":"demo-unverified-integrity-evidence","finalityEvidence":"demo-no-authoritative-finality","meta":{}}}
            """.formatted(DOCUMENT);

    @Test
    void mapsIntrinsicAndRegisteredShapesSeparately() throws Exception {
        var client = new PqsDidQueryClient(null, new ObjectMapper());
        var intrinsic = client.mapIntrinsic("cid", DOCUMENT);
        assertEquals("cid", intrinsic.contractId());
        assertEquals("did:example:alice", intrinsic.id().value());
        assertEquals(4, intrinsic.document().service().getFirst().endpoints().size());
        assertEquals(3, intrinsic.document().service().getFirst().endpoints().get(3).priority());
        assertEquals("http://localhost:42006/v1/dids", intrinsic.document().service().getFirst().endpoints().get(3).uri().toString());
        assertEquals(1, intrinsic.document().service().getFirst().endpoints().get(1).priority());
        assertEquals("http://localhost:42005/v1/dids",
                intrinsic.document().service().getFirst().endpoints().get(2).uri().toString());
        var registered = client.mapRegistered("cid", DOCUMENT, REGISTERED);
        assertEquals("DidDemoAlice::1220-test", registered.registration().registryAdmin());
        assertEquals("cid", registered.intrinsic().contractId());
        for (var document : java.util.List.of(intrinsic.document(), registered.intrinsic().document())) {
            assertEquals(java.util.List.of(), document.verificationMethod());
            var relationships = document.verificationRelationships();
            assertEquals(java.util.List.of(), relationships.authentication());
            assertEquals(java.util.List.of(), relationships.assertionMethod());
            assertEquals(java.util.List.of(), relationships.capabilityInvocation());
            assertEquals(java.util.List.of(), relationships.capabilityDelegation());
            assertEquals(java.util.List.of(), relationships.keyAgreement());
        }
    }

    @Test
    void rejectsMismatchedJoinedPayloads() {
        var client = new PqsDidQueryClient(null, new ObjectMapper());
        assertThrows(java.sql.SQLException.class,
                () -> client.mapRegistered("cid", DOCUMENT, REGISTERED.replace("did:example:alice", "did:example:bob")));
    }

    @Test
    void bindsIntrinsicAndRegisteredQueries() throws Exception {
        AgroalDataSource source = mock(AgroalDataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement intrinsic = mock(PreparedStatement.class);
        PreparedStatement registered = mock(PreparedStatement.class);
        ResultSet rows = mock(ResultSet.class);
        when(source.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(PqsDidQueryClient.FIND_INTRINSIC_BY_DID_SQL)).thenReturn(intrinsic);
        when(connection.prepareStatement(PqsDidQueryClient.FIND_REGISTERED_BY_DID_SQL)).thenReturn(registered);
        when(intrinsic.executeQuery()).thenReturn(rows);
        when(registered.executeQuery()).thenReturn(rows);
        when(rows.next()).thenReturn(false);
        var client = new PqsDidQueryClient(source, new ObjectMapper());
        client.findIntrinsicByDid("did:example:alice");
        client.findRegisteredByDid("did:example:alice");
        verify(intrinsic).setString(1, PqsDidQueryClient.DID_DOCUMENT_INTERFACE);
        verify(intrinsic).setString(2, "did:example:alice");
        verify(registered).setString(1, PqsDidQueryClient.DID_DOCUMENT_INTERFACE);
        verify(registered).setString(2, PqsDidQueryClient.REGISTERED_DID_DOCUMENT_INTERFACE);
        verify(registered).setString(3, "did:example:alice");
    }

    @Test
    void historicalSqlContractAndBindings() throws Exception {
        for (String state : java.util.List.of("active", "archived", "all")) {
            String source = switch (state) {
                case "active" -> "active(?::text, ?::bigint)";
                case "archived" -> "archives(?::text, 0::bigint, ?::bigint)";
                default -> "creates(?::text, 0::bigint, ?::bigint)";
            };
            for (boolean registered : new boolean[] {false, true}) {
                String expected = "with documents as (select * from " + source + "), archived as (select * from archives(?::text, 0::bigint, ?::bigint))"
                        + (registered ? ", registrations as (select * from " + source + ")" : "")
                        + " select d.contract_id, d.payload as document_payload, d.template_fqn, d.payload_type, d.create_event_pk, d.create_event_id, d.created_at_ix, d.created_at_offset, d.created_effective_at, a.archive_event_pk, a.archive_event_id, a.archived_at_ix, a.archived_at_offset, a.archived_effective_at"
                        + (registered ? ", r.payload as registration_payload" : "")
                        + " from documents d left join archived a on d.contract_id = a.contract_id and d.create_event_id = a.create_event_id and d.created_at_offset = a.created_at_offset"
                        + (registered ? " join registrations r on d.contract_id = r.contract_id and d.create_event_id = r.create_event_id and d.created_at_offset = r.created_at_offset and d.created_at_ix = r.created_at_ix"
                            + (state.equals("archived") ? " and d.archive_event_id = r.archive_event_id and d.archived_at_offset = r.archived_at_offset" : "") : "")
                        + " where (?::text is null or (d.payload -> 'id' ->> 'value', d.contract_id) > (?::text, ?::text))"
                        + " and (?::timestamptz is null or d.created_effective_at >= ?::timestamptz)"
                        + " and (?::timestamptz is null or d.created_effective_at < ?::timestamptz)"
                        + " and (?::timestamptz is null or a.archived_effective_at >= ?::timestamptz)"
                        + " and (?::timestamptz is null or a.archived_effective_at < ?::timestamptz)"
                        + (registered ? " and (?::text is null or r.payload -> 'registration' ->> 'registryAdmin' = ?::text)" : "")
                        + " order by d.payload -> 'id' ->> 'value', d.contract_id limit ? offset ?";
                assertEquals(expected, PqsDidQueryClient.historySql(registered, state));
                AgroalDataSource sourceDb = mock(AgroalDataSource.class);
                Connection connection = mock(Connection.class);
                PreparedStatement statement = mock(PreparedStatement.class);
                when(sourceDb.getConnection()).thenReturn(connection);
                when(connection.prepareStatement(expected)).thenReturn(statement);
                when(statement.executeQuery()).thenReturn(mock(ResultSet.class));
                new PqsDidQueryClient(sourceDb, new ObjectMapper()).findHistory(registered,
                        new io.unlockit.application.did.manager.DidListingManager.Filters(state, null, null, null, null, null),
                        9000000000L, "did:example:a", "cid", 51, 100L);
                verify(statement).setString(1, PqsDidQueryClient.DID_DOCUMENT_INTERFACE);
                verify(statement).setLong(2, 9000000000L);
                verify(statement).setString(3, PqsDidQueryClient.DID_DOCUMENT_INTERFACE);
                verify(statement).setLong(4, 9000000000L);
                if (registered) {
                    verify(statement).setString(5, PqsDidQueryClient.REGISTERED_DID_DOCUMENT_INTERFACE);
                    verify(statement).setLong(6, 9000000000L);
                }
                verify(statement).setInt(registered ? 20 : 16, 51);
                verify(statement).setLong(registered ? 21 : 17, 100L);
            }
        }
    }

    @Test
    void effectiveTimePredicatesIncludeFromExcludeUntilAndRejectActiveArchives() throws Exception {
        try (Connection connection = java.sql.DriverManager.getConnection("jdbc:h2:mem:timeBounds")) {
            for (boolean registered : new boolean[] {false, true}) {
                for (String state : java.util.List.of("active", "archived", "all")) {
                    String sql = PqsDidQueryClient.historySql(registered, state);
                    for (String column : java.util.List.of("d.created_effective_at", "a.archived_effective_at")) {
                        for (String operator : java.util.List.of(">=", "<")) {
                            String predicate = "(?::timestamptz is null or " + column + " " + operator + " ?::timestamptz)";
                            assertTrue(sql.contains(predicate));
                            String executable = predicate.replace("?::timestamptz", "cast(? as timestamp with time zone)")
                                    .replace(column, "cast(? as timestamp with time zone)");
                            try (PreparedStatement statement = connection.prepareStatement("select 1 where " + executable)) {
                                String boundary = "2026-09-25T00:00:00Z";
                                for (String event : new String[] {"2026-09-24T23:59:59.999999Z", boundary,
                                        "2026-09-25T00:00:00.000001Z", null}) {
                                    statement.setString(1, boundary);
                                    statement.setString(2, event);
                                    statement.setString(3, boundary);
                                    boolean expected = event != null && (operator.equals(">=")
                                            ? !java.time.Instant.parse(event).isBefore(java.time.Instant.parse(boundary))
                                            : java.time.Instant.parse(event).isBefore(java.time.Instant.parse(boundary)));
                                    try (ResultSet rows = statement.executeQuery()) {
                                        assertEquals(expected, rows.next(), column + " " + operator + " at " + event);
                                    }
                                }
                                statement.setString(1, null);
                                statement.setString(2, null);
                                statement.setString(3, null);
                                try (ResultSet rows = statement.executeQuery()) { assertTrue(rows.next()); }
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    void mapsEveryHistoricalColumnAndPreservesBigint() throws Exception {
        var client = new PqsDidQueryClient(null, new ObjectMapper().findAndRegisterModules());
        ResultSet rows = mock(ResultSet.class);
        when(rows.getString("contract_id")).thenReturn("cid");
        when(rows.getString("document_payload")).thenReturn(DOCUMENT);
        when(rows.getString("registration_payload")).thenReturn(REGISTERED);
        when(rows.getObject("archived_at_offset")).thenReturn(9000000001L);
        for (String name : java.util.List.of("template_fqn", "payload_type", "create_event_id", "archive_event_id"))
            when(rows.getString(name)).thenReturn(name);
        String[][] numbers = {{"createEventPk", "create_event_pk"}, {"createdAtIx", "created_at_ix"}, {"createdAtOffset", "created_at_offset"},
                {"archiveEventPk", "archive_event_pk"}, {"archivedAtIx", "archived_at_ix"}, {"archivedAtOffset", "archived_at_offset"}};
        for (String[] column : numbers) when(rows.getLong(column[1])).thenReturn(9000000001L);
        var instant = java.time.Instant.parse("2026-09-25T00:00:00Z");
        when(rows.getTimestamp("created_effective_at")).thenReturn(java.sql.Timestamp.from(instant));
        when(rows.getTimestamp("archived_effective_at")).thenReturn(java.sql.Timestamp.from(instant));
        for (boolean registered : new boolean[] {false, true}) {
            var item = client.mapHistory(rows, registered);
            var lifecycle = item.path("lifecycle");
            assertEquals("archived", lifecycle.path("state").asText());
            for (String[] column : numbers) assertEquals(9000000001L, lifecycle.path(column[0]).asLong());
            assertEquals("template_fqn", lifecycle.path("templateFqn").asText());
            assertEquals("payload_type", lifecycle.path("payloadType").asText());
            assertEquals("create_event_id", lifecycle.path("createEventId").asText());
            assertEquals("archive_event_id", lifecycle.path("archiveEventId").asText());
            assertEquals(instant.toString(), lifecycle.path("createdEffectiveAt").asText());
            assertEquals(instant.toString(), lifecycle.path("archivedEffectiveAt").asText());
            assertFalse(item.has("observedAt"));
            assertEquals(registered, item.has("registration"));
        }
        when(rows.getObject("archived_at_offset")).thenReturn(null);
        when(rows.getTimestamp("archived_effective_at")).thenReturn(null);
        assertEquals("active", client.mapHistory(rows, false).path("lifecycle").path("state").asText());
        assertTrue(client.mapHistory(rows, false).path("lifecycle").path("archivedEffectiveAt").isNull());
    }

    @Test
    void bootstrapQueryFiltersDsoBeforeDuplicateLimit() throws Exception {
        AgroalDataSource source = mock(AgroalDataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(source.getConnection()).thenReturn(connection);
        when(connection.prepareStatement("select d.contract_id, d.payload as document_payload from active(?) d"
                + " where d.payload -> 'id' ->> 'value' like 'did:canton:DSO::%' order by d.contract_id limit 2")).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(mock(ResultSet.class));
        assertTrue(new PqsDidQueryClient(source, new ObjectMapper()).findDsoCandidates().isEmpty());
        verify(statement).setString(1, PqsDidQueryClient.DID_DOCUMENT_INTERFACE);
    }

    @Test
    void selectsValidatesSnapshotAndFailsUnsupportedSignatures() throws Exception {
        AgroalDataSource source = mock(AgroalDataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement latest = mock(PreparedStatement.class);
        PreparedStatement validate = mock(PreparedStatement.class);
        ResultSet rows = mock(ResultSet.class);
        when(source.getConnection()).thenReturn(connection);
        when(connection.prepareStatement("select set_latest(NULL)")).thenReturn(latest);
        when(connection.prepareStatement("select validate_offset_exists(?::bigint)")).thenReturn(validate);
        when(latest.executeQuery()).thenReturn(rows);
        when(rows.next()).thenReturn(true);
        when(rows.getLong(1)).thenReturn(9000000000L);
        when(validate.executeQuery()).thenReturn(mock(ResultSet.class));
        var client = new PqsDidQueryClient(source, new ObjectMapper());
        assertEquals(9000000000L, client.latestOffset());
        client.validateOffset(9000000000L);
        verify(validate).setLong(1, 9000000000L);
        when(validate.executeQuery()).thenThrow(new java.sql.SQLException("offset unavailable", "P0001"));
        assertEquals("snapshot_unavailable", assertThrows(io.unlockit.application.did.manager.ListingToken.ListingException.class,
                () -> client.validateOffset(9000000000L)).getMessage());
        when(latest.executeQuery()).thenThrow(new java.sql.SQLException("function does not exist", "42883"));
        assertThrows(io.unlockit.domain.did.exception.PqsUnavailableException.class, client::checkProjections);
    }

    @Test
    void sqlUsesActiveInterfacesOrderingAndBoundedPages() {
        assertTrue(PqsDidQueryClient.FIND_INTRINSIC_PAGE_SQL.contains("active(?)"));
        assertFalse(PqsDidQueryClient.FIND_INTRINSIC_PAGE_SQL.contains("join"));
        assertTrue(PqsDidQueryClient.FIND_REGISTERED_PAGE_SQL.contains("join active(?)"));
        assertTrue(PqsDidQueryClient.FIND_REGISTERED_PAGE_SQL.contains("d.contract_id"));
        assertTrue(PqsDidQueryClient.FIND_REGISTERED_PAGE_SQL.contains("limit ? offset ?"));
        assertFalse(PqsDidQueryClient.FIND_REGISTERED_PAGE_SQL.contains("__"));
    }
}
