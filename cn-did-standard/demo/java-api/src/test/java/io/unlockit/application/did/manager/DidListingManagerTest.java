package io.unlockit.application.did.manager;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.unlockit.infrastructure.did.pqs.PqsDidQueryClient;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class DidListingManagerTest {
    final ObjectMapper mapper = new ObjectMapper();
    final PqsDidQueryClient query = mock(PqsDidQueryClient.class);
    final Clock clock = Clock.fixed(Instant.parse("2026-09-25T00:00:00Z"), ZoneOffset.UTC);
    final ListingToken tokens = new ListingToken(mapper, "test-secret-with-at-least-32-bytes", 900, clock);
    final DidListingManager manager = new DidListingManager(query, tokens);

    String first() {
        when(query.latestOffset()).thenReturn(9223372036854770000L);
        when(query.findHistory(anyBoolean(), any(), anyLong(), nullable(String.class), nullable(String.class), anyInt(), anyLong()))
                .thenReturn(List.of(mapper.createObjectNode().put("did", "did:example:a").put("contractId", "a"),
                        mapper.createObjectNode().put("did", "did:example:b").put("contractId", "b")));
        var result = manager.list("dids", "all", null, "2026-01-01T00:00:00Z", null, null, null, "2", "1", null);
        assertEquals(9223372036854770000L, result.snapshotOffset());
        assertEquals(2, result.page());
        assertTrue(result.hasNext());
        assertEquals(1, result.items().size());
        return result.nextPageToken();
    }

    @Test void pinsSnapshotAndUsesKeysetOrExplicitOffset() {
        String token = first();
        var next = manager.list("dids", null, null, null, null, null, null, null, null, token);
        assertEquals(3, next.page());
        verify(query).findHistory(eq(false), any(), eq(9223372036854770000L), eq("did:example:a"), eq("a"), eq(2), eq(0L));
        manager.list("dids", "all", null, "2026-01-01T00:00:00Z", null, null, null, "7", "1", token);
        verify(query).findHistory(eq(false), any(), eq(9223372036854770000L), isNull(), isNull(), eq(2), eq(7L));
        verify(query, times(1)).latestOffset();
        verify(query, times(3)).validateOffset(9223372036854770000L);
    }

    @Test void rejectsWrongResourceAndRepeatedQueryChanges() {
        String token = first();
        assertCode("token_wrong_resource", () -> manager.list("registered-dids", null, null, null, null, null, null, null, null, token));
        assertCode("token_query_mismatch", () -> manager.list("dids", "active", null, null, null, null, null, null, null, token));
        assertCode("token_query_mismatch", () -> manager.list("dids", null, null, null, null, null, null, null, "2", token));
        assertCode("token_query_mismatch", () -> manager.list("dids", null, null, "2025-01-01T00:00:00Z", null, null, null, null, null, token));
        assertCode("token_query_mismatch", () -> manager.list("dids", null, null, null, null, "2026-01-01T00:00:00Z", null, null, null, token));
    }

    @Test void authenticatesExpiresAndRejectsSnapshotLoss() {
        String token = first();
        assertCode("invalid_token", () -> tokens.decode("X" + token.substring(1)));
        assertCode("invalid_token", () -> tokens.decode(token + ".extra"));
        assertCode("invalid_token", () -> tokens.decode("garbage"));
        var otherKey = new ListingToken(mapper, "different-secret-with-at-least-32-bytes", 900, clock);
        assertCode("invalid_token", () -> otherKey.decode(token));
        var expired = new ListingToken(mapper, "test-secret-with-at-least-32-bytes", 900, Clock.offset(clock, java.time.Duration.ofSeconds(900)));
        assertCode("token_expired", () -> expired.decode(token));
        doThrow(new ListingToken.ListingException("snapshot_unavailable")).when(query).validateOffset(anyLong());
        assertCode("snapshot_unavailable", () -> manager.list("dids", null, null, null, null, null, null, null, null, token));
    }

    @Test void validatesFreshQueriesAndTerminalPage() {
        when(query.latestOffset()).thenReturn(42L);
        when(query.findHistory(anyBoolean(), any(), anyLong(), nullable(String.class), nullable(String.class), anyInt(), anyLong())).thenReturn(List.of());
        var page = manager.list("dids", null, null, null, null, null, null, null, null, null);
        assertNull(page.nextPageToken());
        assertFalse(page.hasNext());
        assertEquals(50, page.pageSize());
        for (String state : List.of("active", "archived", "all"))
            manager.list("dids", state, null, null, null, null, null, null, null, null);
        assertThrows(IllegalArgumentException.class, () -> manager.list("dids", "bad", null, null, null, null, null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> manager.list("dids", null, null, "2026-02-01T00:00:00Z", "2026-01-01T00:00:00Z", null, null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> manager.list("dids", null, null, null, null, null, null, "9223372036854775807", "100", null));
        assertThrows(IllegalArgumentException.class, () -> new ListingToken(mapper, "short", 1, clock));
    }

    @Test void rejectsEqualAndReversedIntervalsBeforeQuerying() {
        String from = "2026-09-25T00:00:00Z";
        for (String until : List.of(from, "2026-09-25T01:00:00+01:00", "2026-09-24T00:00:00Z")) {
            assertThrows(IllegalArgumentException.class,
                    () -> manager.list("dids", "all", null, from, until, null, null, null, null, null));
            assertThrows(IllegalArgumentException.class,
                    () -> manager.list("registered-dids", "all", null, null, null, from, until, null, null, null));
        }
        verifyNoInteractions(query);
    }

    @Test void tokensBindCanonicalNormalizedUntilClaims() throws Exception {
        first();
        String from = "2026-01-01T00:00:00Z";
        String until = "2026-10-01T00:00:00Z";
        var page = manager.list("dids", "all", null, from, "2026-10-01T01:00:00+01:00",
                from, until, null, "1", null);
        String token = page.nextPageToken();
        var claims = mapper.readTree(java.util.Base64.getUrlDecoder().decode(token.split("\\.")[0])).path("filters");
        assertEquals(java.util.Set.of("state", "partyId", "createdFrom", "createdUntil", "archivedFrom", "archivedUntil"),
                mapper.convertValue(claims, java.util.Map.class).keySet());
        assertEquals(until, claims.path("createdUntil").asText());
        assertEquals(until, claims.path("archivedUntil").asText());
        var bound = tokens.decode(token).filters();
        assertEquals(until, bound.createdUntil());
        assertEquals(until, bound.archivedUntil());
        manager.list("dids", null, null, null, null, null, null, null, null, token);
        manager.list("dids", "all", null, from, until, from, until, null, "1", token);
        verify(query, times(2)).findHistory(eq(false), eq(bound), anyLong(), eq("did:example:a"), eq("a"), eq(2), eq(0L));
        assertCode("token_query_mismatch", () -> manager.list("dids", null, null, null, from, null, null, null, null, token));
        assertCode("token_query_mismatch", () -> manager.list("dids", null, null, null, null, null, from, null, null, token));
    }

    static void assertCode(String code, org.junit.jupiter.api.function.Executable operation) {
        assertEquals(code, assertThrows(ListingToken.ListingException.class, operation).getMessage());
    }
}
