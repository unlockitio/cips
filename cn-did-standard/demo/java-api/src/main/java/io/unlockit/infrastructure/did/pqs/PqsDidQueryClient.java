package io.unlockit.infrastructure.did.pqs;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agroal.api.AgroalDataSource;
import io.unlockit.domain.did.entity.DidDocument;
import io.unlockit.domain.did.entity.DidDocumentMetadata;
import io.unlockit.domain.did.entity.DidRegistration;
import io.unlockit.domain.did.entity.DidResolutionMetadata;
import io.unlockit.domain.did.entity.IntrinsicDid;
import io.unlockit.domain.did.entity.RegisteredDid;
import io.unlockit.domain.did.entity.ServiceEndpoint;
import io.unlockit.domain.did.entity.ServiceEntry;
import io.unlockit.domain.did.entity.VerificationMethod;
import io.unlockit.domain.did.entity.VerificationRelationships;
import io.unlockit.domain.did.exception.PqsUnavailableException;
import io.unlockit.domain.did.value_object.DidValue;
import io.unlockit.domain.did.value_object.PartyId;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.net.URI;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class PqsDidQueryClient {
    static final String DID_DOCUMENT_INTERFACE =
            "canton-network-did-interfaces:Canton.Network.Did.V1:DidDocument";
    static final String REGISTERED_DID_DOCUMENT_INTERFACE =
            "canton-network-did-interfaces:Canton.Network.Did.V1:RegisteredDidDocument";
    static final String FIND_INTRINSIC_PAGE_SQL = """
            select d.contract_id, d.payload as document_payload
              from active(?) d
             order by d.payload -> 'id' ->> 'value', d.contract_id
             limit ? offset ?
            """;
    static final String FIND_INTRINSIC_BY_DID_SQL = """
            select d.contract_id, d.payload as document_payload
              from active(?) d
             where d.payload -> 'id' ->> 'value' = ?
             order by d.contract_id limit 2
            """;
    static final String REGISTERED_SELECT = """
            select d.contract_id, d.payload as document_payload, r.payload as registration_payload
              from active(?) d join active(?) r using (contract_id)
            """;
    static final String FIND_REGISTERED_PAGE_SQL = REGISTERED_SELECT
            + " order by d.payload -> 'id' ->> 'value', d.contract_id limit ? offset ?";
    static final String FIND_REGISTERED_BY_DID_SQL = REGISTERED_SELECT
            + " where d.payload -> 'id' ->> 'value' = ? order by d.contract_id limit 2";
    static final String FIND_REGISTERED_BY_PARTY_SQL = REGISTERED_SELECT
            + " where r.payload -> 'registration' ->> 'registryAdmin' = ?"
            + " order by d.payload -> 'id' ->> 'value', d.contract_id limit ? offset ?";
    static final String READINESS_SQL =
            "select 1 from active(?) d join active(?) r using (contract_id) limit 1";

    private final AgroalDataSource dataSource;
    private final ObjectMapper objectMapper;

    @Inject
    public PqsDidQueryClient(AgroalDataSource dataSource, ObjectMapper objectMapper) {
        this.dataSource = dataSource;
        this.objectMapper = objectMapper;
    }

    public List<IntrinsicDid> findIntrinsicPage(long offset, int limit) {
        return queryIntrinsic(FIND_INTRINSIC_PAGE_SQL, statement -> {
            statement.setString(1, DID_DOCUMENT_INTERFACE);
            statement.setInt(2, limit);
            statement.setLong(3, offset);
        });
    }

    public List<IntrinsicDid> findDsoCandidates() {
        return queryIntrinsic("select d.contract_id, d.payload as document_payload from active(?) d"
                + " where d.payload -> 'id' ->> 'value' like 'did:canton:DSO::%' order by d.contract_id limit 2",
                statement -> statement.setString(1, DID_DOCUMENT_INTERFACE));
    }

    public List<IntrinsicDid> findIntrinsicByDid(String did) {
        return queryIntrinsic(FIND_INTRINSIC_BY_DID_SQL, statement -> {
            statement.setString(1, DID_DOCUMENT_INTERFACE);
            statement.setString(2, did);
        });
    }

    public List<RegisteredDid> findRegisteredPage(long offset, int limit) {
        return queryRegistered(FIND_REGISTERED_PAGE_SQL, statement -> {
            bindInterfaces(statement);
            statement.setInt(3, limit);
            statement.setLong(4, offset);
        });
    }

    public List<RegisteredDid> findRegisteredByDid(String did) {
        return queryRegistered(FIND_REGISTERED_BY_DID_SQL, statement -> {
            bindInterfaces(statement);
            statement.setString(3, did);
        });
    }

    public List<RegisteredDid> findRegisteredByPartyId(String partyId, long offset, int limit) {
        return queryRegistered(FIND_REGISTERED_BY_PARTY_SQL, statement -> {
            bindInterfaces(statement);
            statement.setString(3, partyId);
            statement.setInt(4, limit);
            statement.setLong(5, offset);
        });
    }

    public long latestOffset() {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement("select set_latest(NULL)" );
                ResultSet rows = statement.executeQuery()) {
            if (!rows.next()) throw new SQLException("PQS did not select a latest offset");
            long offset = rows.getLong(1);
            if (rows.wasNull()) throw new SQLException("PQS latest offset is unavailable");
            return offset;
        } catch (SQLException e) { throw new PqsUnavailableException(e); }
    }

    public void validateOffset(long offset) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement("select validate_offset_exists(?::bigint)")) {
            statement.setLong(1, offset);
            statement.executeQuery().close();
        } catch (SQLException e) {
            if ("P0001".equals(e.getSQLState()) || "22023".equals(e.getSQLState()))
                throw new io.unlockit.application.did.manager.ListingToken.ListingException("snapshot_unavailable");
            throw new PqsUnavailableException(e);
        }
    }

    static String historySource(String state) {
        return switch (state) {
            case "active" -> "active(?::text, ?::bigint)";
            case "archived" -> "archives(?::text, 0::bigint, ?::bigint)";
            case "all" -> "creates(?::text, 0::bigint, ?::bigint)";
            default -> throw new IllegalArgumentException("Invalid lifecycle state");
        };
    }

    static String historySql(boolean registered, String state) {
        String source = historySource(state);
        return "with documents as (select * from " + source + "), "
                + "archived as (select * from archives(?::text, 0::bigint, ?::bigint))"
                + (registered ? ", registrations as (select * from " + source + ")" : "")
                + " select d.contract_id, d.payload as document_payload, d.template_fqn, d.payload_type,"
                + " d.create_event_pk, d.create_event_id, d.created_at_ix, d.created_at_offset, d.created_effective_at,"
                + " a.archive_event_pk, a.archive_event_id, a.archived_at_ix, a.archived_at_offset, a.archived_effective_at"
                + (registered ? ", r.payload as registration_payload" : "")
                + " from documents d left join archived a on d.contract_id = a.contract_id"
                + " and d.create_event_id = a.create_event_id and d.created_at_offset = a.created_at_offset"
                + (registered ? " join registrations r on d.contract_id = r.contract_id"
                    + " and d.create_event_id = r.create_event_id and d.created_at_offset = r.created_at_offset"
                    + " and d.created_at_ix = r.created_at_ix"
                    + (state.equals("archived") ? " and d.archive_event_id = r.archive_event_id and d.archived_at_offset = r.archived_at_offset" : "") : "")
                + " where (?::text is null or (d.payload -> 'id' ->> 'value', d.contract_id) > (?::text, ?::text))"
                + " and (?::timestamptz is null or d.created_effective_at >= ?::timestamptz)"
                + " and (?::timestamptz is null or d.created_effective_at < ?::timestamptz)"
                + " and (?::timestamptz is null or a.archived_effective_at >= ?::timestamptz)"
                + " and (?::timestamptz is null or a.archived_effective_at < ?::timestamptz)"
                + (registered ? " and (?::text is null or r.payload -> 'registration' ->> 'registryAdmin' = ?::text)" : "")
                + " order by d.payload -> 'id' ->> 'value', d.contract_id limit ? offset ?";
    }

    public List<com.fasterxml.jackson.databind.node.ObjectNode> findHistory(boolean registered,
            io.unlockit.application.did.manager.DidListingManager.Filters filters, long snapshot,
            String lastDid, String lastContract, int limit, long offset) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(historySql(registered, filters.state()))) {
            int i = 1;
            statement.setString(i++, DID_DOCUMENT_INTERFACE);
            statement.setLong(i++, snapshot);
            statement.setString(i++, DID_DOCUMENT_INTERFACE);
            statement.setLong(i++, snapshot);
            if (registered) {
                statement.setString(i++, REGISTERED_DID_DOCUMENT_INTERFACE);
                statement.setLong(i++, snapshot);
            }
            statement.setString(i++, lastDid);
            statement.setString(i++, lastDid);
            statement.setString(i++, lastContract);
            for (String value : new String[] {filters.createdFrom(), filters.createdUntil(), filters.archivedFrom(), filters.archivedUntil()}) {
                statement.setString(i++, value);
                statement.setString(i++, value);
            }
            if (registered) {
                statement.setString(i++, filters.partyId());
                statement.setString(i++, filters.partyId());
            }
            statement.setInt(i++, limit);
            statement.setLong(i, offset);
            List<com.fasterxml.jackson.databind.node.ObjectNode> result = new ArrayList<>();
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) result.add(mapHistory(rows, registered));
            }
            return result;
        } catch (SQLException e) { throw new PqsUnavailableException(e); }
    }

    com.fasterxml.jackson.databind.node.ObjectNode mapHistory(ResultSet rows, boolean registered) throws SQLException {
        Object representation = registered
                ? io.unlockit.application.did.mapper.DidRepresentationMapper.toRepresentation(mapRegistered(
                    rows.getString("contract_id"), rows.getString("document_payload"), rows.getString("registration_payload")))
                : io.unlockit.application.did.mapper.DidRepresentationMapper.toRepresentation(mapIntrinsic(
                    rows.getString("contract_id"), rows.getString("document_payload")));
        com.fasterxml.jackson.databind.node.ObjectNode item = objectMapper.valueToTree(representation);
        var lifecycle = item.putObject("lifecycle");
        lifecycle.put("state", rows.getObject("archived_at_offset") == null ? "active" : "archived");
        String[][] columns = {{"templateFqn", "template_fqn"}, {"payloadType", "payload_type"},
                {"createEventId", "create_event_id"}, {"archiveEventId", "archive_event_id"}};
        for (String[] column : columns) lifecycle.put(column[0], rows.getString(column[1]));
        String[][] numbers = {{"createEventPk", "create_event_pk"}, {"createdAtIx", "created_at_ix"},
                {"createdAtOffset", "created_at_offset"}, {"archiveEventPk", "archive_event_pk"},
                {"archivedAtIx", "archived_at_ix"}, {"archivedAtOffset", "archived_at_offset"}};
        for (String[] column : numbers) {
            long value = rows.getLong(column[1]);
            if (rows.wasNull()) lifecycle.putNull(column[0]); else lifecycle.put(column[0], value);
        }
        for (String[] column : new String[][] {{"createdEffectiveAt", "created_effective_at"}, {"archivedEffectiveAt", "archived_effective_at"}}) {
            java.sql.Timestamp value = rows.getTimestamp(column[1]);
            if (value == null) lifecycle.putNull(column[0]); else lifecycle.put(column[0], value.toInstant().toString());
        }
        return item;
    }

    public void checkProjections() {
        long snapshot = latestOffset();
        validateOffset(snapshot);
        for (String state : List.of("active", "archived", "all")) {
            findHistory(true, new io.unlockit.application.did.manager.DidListingManager.Filters(state, null, null, null, null, null),
                    snapshot, null, null, 1, 0);
        }
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(READINESS_SQL)) {
            bindInterfaces(statement);
            statement.executeQuery();
        } catch (SQLException exception) {
            throw new PqsUnavailableException(exception);
        }
    }

    private List<IntrinsicDid> queryIntrinsic(String sql, StatementBinder binder) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            binder.bind(statement);
            List<IntrinsicDid> results = new ArrayList<>();
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    results.add(mapIntrinsic(rows.getString("contract_id"), rows.getString("document_payload")));
                }
            }
            return results;
        } catch (SQLException exception) {
            throw new PqsUnavailableException(exception);
        }
    }

    private List<RegisteredDid> queryRegistered(String sql, StatementBinder binder) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            binder.bind(statement);
            List<RegisteredDid> results = new ArrayList<>();
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    results.add(mapRegistered(rows.getString("contract_id"), rows.getString("document_payload"),
                            rows.getString("registration_payload")));
                }
            }
            return results;
        } catch (SQLException exception) {
            throw new PqsUnavailableException(exception);
        }
    }

    IntrinsicDid mapIntrinsic(String contractId, String documentPayload) throws SQLException {
        try {
            JsonNode document = objectMapper.readTree(documentPayload);
            DidValue id = new DidValue(value(document.path("id")));
            return new IntrinsicDid(contractId, id, document(document, id));
        } catch (RuntimeException | JsonProcessingException exception) {
            throw new SQLException("PQS returned an unsupported DidDocument payload", exception);
        }
    }

    RegisteredDid mapRegistered(String contractId, String documentPayload, String registrationPayload)
            throws SQLException {
        try {
            IntrinsicDid intrinsic = mapIntrinsic(contractId, documentPayload);
            JsonNode registered = objectMapper.readTree(registrationPayload);
            JsonNode document = objectMapper.readTree(documentPayload);
            if (!registered.path("document").equals(document)) {
                throw new IllegalArgumentException("joined DID interface projections disagree on document");
            }
            JsonNode registration = registered.path("registration");
            Instant registeredAt = instant(registration.path("registeredAt"))
                    .orElseThrow(() -> new IllegalArgumentException("registration timestamp missing"));
            Instant updated = instant(registration.path("updatedAt")).orElse(registeredAt);
            String registryAdmin = text(registration.path("registryAdmin"));
            String versionId = text(registration.path("versionId"));
            String source = text(registration.path("source"));
            String integrity = optionalText(registration.path("integrityEvidence"))
                    .orElse("demo-no-integrity-evidence");
            String finality = optionalText(registration.path("finalityEvidence"))
                    .orElse("demo-no-finality-evidence");
            DidRegistration registrationValue = new DidRegistration(registryAdmin, registeredAt, updated,
                    versionId, registration.path("deactivated").asBoolean(),
                    instant(registration.path("deactivatedAt")).orElse(null), source, integrity, finality,
                    registration.path("meta"));
            return new RegisteredDid(intrinsic, registrationValue,
                    new DidDocumentMetadata(versionId, updated, registrationValue.deactivated()),
                    new DidResolutionMetadata(source, integrity, Instant.now(),
                            "pqs-active-projection-eventual-consistency", finality),
                    Optional.of(new PartyId(registryAdmin)));
        } catch (RuntimeException | JsonProcessingException exception) {
            throw new SQLException("PQS returned an unsupported RegisteredDidDocument payload", exception);
        }
    }

    private DidDocument document(JsonNode document, DidValue id) {
        return new DidDocument(id,
                stream(document.path("controllers")).map(node -> new DidValue(value(node))).toList(),
                stream(document.path("verificationMethods")).map(this::verificationMethod).toList(),
                relationships(document.path("verificationRelationships")),
                stream(document.path("services")).map(this::service).toList());
    }

    private VerificationMethod verificationMethod(JsonNode node) {
        return new VerificationMethod(value(node.path("id")), text(node.path("methodType")),
                new DidValue(value(node.path("controllerDid"))), text(node.path("verificationMaterial")));
    }

    private VerificationRelationships relationships(JsonNode node) {
        return new VerificationRelationships(values(node.path("authentication")), values(node.path("assertionMethod")),
                values(node.path("capabilityInvocation")), List.of(), List.of());
    }

    private ServiceEntry service(JsonNode node) {
        JsonNode endpoint = node.path("serviceEndpoint");
        List<ServiceEndpoint> endpoints = stream(endpoint.path("endpoints"))
                .map(this::serviceEndpoint)
                .toList();
        return new ServiceEntry(value(node.path("id")), value(node.path("serviceType")),
                text(endpoint.path("version")), endpoints);
    }

    private ServiceEndpoint serviceEndpoint(JsonNode node) {
        return new ServiceEndpoint(URI.create(value(node.path("uri"))), priority(node.path("priority")));
    }

    private static int priority(JsonNode node) {
        if (node.isIntegralNumber()) return node.intValue();
        String value = text(node);
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("expected integer service endpoint priority", exception);
        }
    }

    private static void bindInterfaces(PreparedStatement statement) throws SQLException {
        statement.setString(1, DID_DOCUMENT_INTERFACE);
        statement.setString(2, REGISTERED_DID_DOCUMENT_INTERFACE);
    }

    private static List<String> values(JsonNode array) {
        return stream(array).map(PqsDidQueryClient::value).toList();
    }

    private static java.util.stream.Stream<JsonNode> stream(JsonNode collection) {
        if (collection.isArray()) return java.util.stream.StreamSupport.stream(collection.spliterator(), false);
        if (collection.isObject() && collection.has("hd") && collection.path("tl").isArray()) {
            return java.util.stream.Stream.concat(java.util.stream.Stream.of(collection.path("hd")),
                    java.util.stream.StreamSupport.stream(collection.path("tl").spliterator(), false));
        }
        throw new IllegalArgumentException("expected Daml list or NonEmpty payload");
    }

    private static String value(JsonNode wrapper) { return text(wrapper.path("value")); }
    private static Optional<Instant> instant(JsonNode node) { return optionalText(node).map(Instant::parse); }
    private static Optional<String> optionalText(JsonNode node) {
        return node.isMissingNode() || node.isNull() ? Optional.empty() : Optional.of(text(node));
    }
    private static String text(JsonNode node) {
        if (!node.isTextual() || node.textValue().isBlank())
            throw new IllegalArgumentException("expected nonblank text in PQS payload");
        return node.textValue();
    }

    @FunctionalInterface
    private interface StatementBinder { void bind(PreparedStatement statement) throws SQLException; }
}
