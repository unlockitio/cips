package io.unlockit.application.did.manager;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.unlockit.infrastructure.did.pqs.PqsDidQueryClient;
import io.unlockit.application.did.manager.ListingToken.Cursor;
import io.unlockit.application.did.manager.ListingToken.ListingException;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

@ApplicationScoped
public class DidListingManager {
    private final PqsDidQueryClient query;
    private final ListingToken tokens;

    public DidListingManager(PqsDidQueryClient query, ListingToken tokens) {
        this.query = query;
        this.tokens = tokens;
    }

    public Listing list(String resource, String state, String partyId, String createdFrom, String createdUntil,
            String archivedFrom, String archivedUntil, String pageValue, String sizeValue, String token) {
        Cursor cursor = token == null ? null : tokens.decode(token);
        if (cursor != null && !resource.equals(cursor.resource())) throw new ListingException("token_wrong_resource");
        Filters supplied = new Filters(state, partyId, time(createdFrom), time(createdUntil), time(archivedFrom), time(archivedUntil));
        Filters filters = cursor == null ? supplied.defaults() : supplied.merge(cursor.filters());
        filters.validate();
        int size = sizeValue == null ? (cursor == null ? 50 : cursor.pageSize()) : Integer.parseInt(sizeValue);
        if (size < 1 || size > 100) throw new IllegalArgumentException("pageSize must be between 1 and 100");
        if (cursor != null && size != cursor.pageSize()) throw new ListingException("token_query_mismatch");
        long page;
        long offset;
        try {
            page = pageValue == null ? (cursor == null ? 0 : Math.addExact(cursor.page(), 1)) : Long.parseLong(pageValue);
            if (page < 0) throw new IllegalArgumentException("page must be nonnegative");
            offset = Math.multiplyExact(page, size);
        } catch (ArithmeticException e) { throw new IllegalArgumentException("pagination overflow", e); }
        long snapshot = cursor == null ? query.latestOffset() : cursor.snapshotOffset();
        query.validateOffset(snapshot);
        boolean keyset = cursor != null && pageValue == null;
        List<ObjectNode> rows = query.findHistory(resource.equals("registered-dids"), filters, snapshot,
                keyset ? cursor.lastDid() : null, keyset ? cursor.lastContractId() : null, size + 1, keyset ? 0 : offset);
        boolean hasNext = rows.size() > size;
        List<ObjectNode> items = rows.stream().limit(size).toList();
        String next = null;
        if (hasNext) {
            ObjectNode last = items.getLast();
            next = tokens.encode(new Cursor(1, resource, filters, size, snapshot, page,
                    last.path("did").asText(), last.path("contractId").asText(), cursor == null ? tokens.expiry() : cursor.expiresAt()));
        }
        return new Listing(items, snapshot, next, page, size, hasNext);
    }

    private static String time(String value) { return value == null ? null : Instant.parse(value).toString(); }

    public record Listing(List<ObjectNode> items, long snapshotOffset, String nextPageToken, long page, int pageSize, boolean hasNext) {}

    public record Filters(String state, String partyId, String createdFrom, String createdUntil, String archivedFrom, String archivedUntil) {
        Filters defaults() { return new Filters(state == null ? "active" : state, partyId, createdFrom, createdUntil, archivedFrom, archivedUntil); }
        Filters merge(Filters bound) {
            return new Filters(match(state, bound.state), match(partyId, bound.partyId), match(createdFrom, bound.createdFrom),
                    match(createdUntil, bound.createdUntil), match(archivedFrom, bound.archivedFrom), match(archivedUntil, bound.archivedUntil));
        }
        private static String match(String value, String bound) {
            if (value != null && !Objects.equals(value, bound)) throw new ListingException("token_query_mismatch");
            return bound;
        }
        void validate() {
            if (!List.of("active", "archived", "all").contains(state)) throw new IllegalArgumentException("state must be active, archived or all");
            if (partyId != null) new io.unlockit.domain.did.value_object.PartyId(partyId);
            range(createdFrom, createdUntil);
            range(archivedFrom, archivedUntil);
        }
        private static void range(String from, String until) {
            if (from != null && until != null && !Instant.parse(from).isBefore(Instant.parse(until)))
                throw new IllegalArgumentException("time range requires From < Until");
        }
    }
}
