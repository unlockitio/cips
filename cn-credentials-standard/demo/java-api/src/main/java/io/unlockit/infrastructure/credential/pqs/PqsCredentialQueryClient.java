package io.unlockit.infrastructure.credential.pqs;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agroal.api.AgroalDataSource;
import io.unlockit.domain.credential.exception.PqsUnavailableException;
import io.unlockit.domain.credential.model.Credential;
import io.unlockit.domain.credential.model.CredentialRegistryFactory;
import io.unlockit.domain.credential.model.RegisteredCredential;
import io.unlockit.domain.credential.model.Registration;
import io.unlockit.domain.credential.query.CredentialQueryClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class PqsCredentialQueryClient implements CredentialQueryClient {
  static final String CREDENTIAL_INTERFACE =
      "canton-network-credentials-interfaces:Canton.Network.Credentials.V1:Credential";
  static final String REGISTERED_CREDENTIAL_INTERFACE =
      "canton-network-credentials-interfaces:Canton.Network.Credentials.V1:RegisteredCredential";
  static final String CREDENTIAL_REGISTRY_FACTORY_INTERFACE =
      "canton-network-credentials-interfaces:Canton.Network.Credentials.V1:CredentialRegistryFactory";
  static final String FIND_CREDENTIAL_BY_ID_SQL = """
      select c.contract_id, c.payload as credential_payload
        from active(?) c
       where c.payload ->> 'id' = ?
       order by c.payload ->> 'id', c.contract_id
       limit 2
      """;
  static final String FIND_CREDENTIAL_PAGE_SQL = """
      select c.contract_id, c.payload as credential_payload
        from active(?) c
       order by c.payload ->> 'id', c.contract_id
       limit ? offset ?
      """;
  static final String FIND_REGISTERED_BY_ID_SQL = """
      select c.contract_id, c.payload as credential_payload, r.payload as registration_payload
        from active(?) c join active(?) r using (contract_id)
       where c.payload ->> 'id' = ?
       order by c.payload ->> 'id', c.contract_id
       limit 2
      """;
  static final String FIND_REGISTERED_PAGE_SQL = """
      select c.contract_id, c.payload as credential_payload, r.payload as registration_payload
        from active(?) c join active(?) r using (contract_id)
       order by c.payload ->> 'id', c.contract_id
       limit ? offset ?
      """;
  static final String FIND_CREDENTIAL_REGISTRY_IDS_SQL = """
      select f.payload ->> 'registryAdmin' as registry_id
        from active(?) f
       group by f.payload ->> 'registryAdmin'
       order by registry_id
       limit ? offset ?
      """;
  static final String FIND_CREDENTIAL_REGISTRY_FACTORIES_SQL = """
      select f.contract_id, f.payload as factory_payload
        from active(?) f
       where f.payload ->> 'registryAdmin' = ?
       order by f.contract_id
      """;
  static final String READINESS_SQL = "select 1 from active(?) limit 0";

  private final AgroalDataSource dataSource;
  private final ObjectMapper objectMapper;

  @Inject
  public PqsCredentialQueryClient(AgroalDataSource dataSource, ObjectMapper objectMapper) {
    this.dataSource = dataSource;
    this.objectMapper = objectMapper;
  }

  @Override
  public List<Credential> findCredentialById(String credentialId) {
    return queryCredentials(
        FIND_CREDENTIAL_BY_ID_SQL,
        statement -> {
          statement.setString(1, CREDENTIAL_INTERFACE);
          statement.setString(2, credentialId);
        });
  }

  @Override
  public List<Credential> findCredentialPage(long offset, int limit) {
    return queryCredentials(
        FIND_CREDENTIAL_PAGE_SQL,
        statement -> {
          statement.setString(1, CREDENTIAL_INTERFACE);
          statement.setInt(2, limit);
          statement.setLong(3, offset);
        });
  }

  @Override
  public List<RegisteredCredential> findRegisteredCredentialById(String credentialId) {
    return queryRegistered(
        FIND_REGISTERED_BY_ID_SQL,
        statement -> {
          statement.setString(1, CREDENTIAL_INTERFACE);
          statement.setString(2, REGISTERED_CREDENTIAL_INTERFACE);
          statement.setString(3, credentialId);
        });
  }

  @Override
  public List<RegisteredCredential> findRegisteredCredentialPage(long offset, int limit) {
    return queryRegistered(
        FIND_REGISTERED_PAGE_SQL,
        statement -> {
          statement.setString(1, CREDENTIAL_INTERFACE);
          statement.setString(2, REGISTERED_CREDENTIAL_INTERFACE);
          statement.setInt(3, limit);
          statement.setLong(4, offset);
        });
  }

  @Override
  public List<String> findCredentialRegistryIds(long offset, int limit) {
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(FIND_CREDENTIAL_REGISTRY_IDS_SQL)) {
      statement.setString(1, CREDENTIAL_REGISTRY_FACTORY_INTERFACE);
      statement.setInt(2, limit);
      statement.setLong(3, offset);
      List<String> registryIds = new ArrayList<>();
      try (ResultSet rows = statement.executeQuery()) {
        while (rows.next()) {
          registryIds.add(rows.getString("registry_id"));
        }
      }
      return registryIds;
    } catch (SQLException exception) {
      throw new PqsUnavailableException(exception);
    }
  }

  @Override
  public List<CredentialRegistryFactory> findCredentialRegistryFactories(String registryId) {
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement =
            connection.prepareStatement(FIND_CREDENTIAL_REGISTRY_FACTORIES_SQL)) {
      statement.setString(1, CREDENTIAL_REGISTRY_FACTORY_INTERFACE);
      statement.setString(2, registryId);
      List<CredentialRegistryFactory> factories = new ArrayList<>();
      try (ResultSet rows = statement.executeQuery()) {
        while (rows.next()) {
          factories.add(
              mapCredentialRegistryFactory(
                  rows.getString("contract_id"), rows.getString("factory_payload")));
        }
      }
      return factories;
    } catch (SQLException exception) {
      throw new PqsUnavailableException(exception);
    }
  }

  @Override
  public long latestOffset() {
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement("select set_latest(NULL)");
        ResultSet rows = statement.executeQuery()) {
      if (!rows.next()) throw new SQLException("PQS did not select a latest offset");
      long offset = rows.getLong(1);
      if (rows.wasNull()) throw new SQLException("PQS latest offset is unavailable");
      return offset;
    } catch (SQLException e) { throw new PqsUnavailableException(e); }
  }

  @Override
  public void validateOffset(long offset) {
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement("select validate_offset_exists(?::bigint)")) {
      statement.setLong(1, offset);
      statement.executeQuery().close();
    } catch (SQLException e) {
      if ("P0001".equals(e.getSQLState()) || "22023".equals(e.getSQLState()))
        throw new io.unlockit.domain.credential.exception.InvalidPaginationException("snapshot_unavailable", e);
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

  static String historySql(boolean registered, String state, boolean singular) {
    String source = historySource(state);
    return "with credentials as (select * from " + source + "), "
        + "archived as (select * from archives(?::text, 0::bigint, ?::bigint))"
        + (registered ? ", registrations as (select * from " + source + ")" : "")
        + " select c.contract_id, c.payload as credential_payload, c.template_fqn, c.payload_type,"
        + " c.create_event_pk, c.create_event_id, c.created_at_ix, c.created_at_offset, c.created_effective_at,"
        + " a.archive_event_pk, a.archive_event_id, a.archived_at_ix, a.archived_at_offset, a.archived_effective_at"
        + (registered ? ", r.payload as registration_payload" : "")
        + " from credentials c left join archived a on c.contract_id = a.contract_id"
        + " and c.create_event_id = a.create_event_id and c.created_at_offset = a.created_at_offset"
        + " and c.created_at_ix = a.created_at_ix"
        + (registered ? " join registrations r on c.contract_id = r.contract_id"
            + " and c.create_event_id = r.create_event_id and c.created_at_offset = r.created_at_offset"
            + " and c.created_at_ix = r.created_at_ix"
            + (state.equals("archived") ? " and c.archive_event_id = r.archive_event_id"
                + " and c.archived_at_offset = r.archived_at_offset and c.archived_at_ix = r.archived_at_ix"
                : state.equals("all") ? " and ((c.archive_event_id is null and r.archive_event_id is null"
                    + " and c.archived_at_offset is null and r.archived_at_offset is null"
                    + " and c.archived_at_ix is null and r.archived_at_ix is null)"
                    + " or (c.archive_event_id = r.archive_event_id"
                    + " and c.archived_at_offset = r.archived_at_offset and c.archived_at_ix = r.archived_at_ix))" : "") : "")
        + " where (?::text is null or (c.payload ->> 'id', c.contract_id) > (?::text, ?::text))"
        + " and (?::text is null or c.payload ->> 'id' = ?::text)"
        + " and (?::timestamptz is null or c.created_effective_at >= ?::timestamptz)"
        + " and (?::timestamptz is null or c.created_effective_at < ?::timestamptz)"
        + " and (?::timestamptz is null or a.archived_effective_at >= ?::timestamptz)"
        + " and (?::timestamptz is null or a.archived_effective_at < ?::timestamptz)"
        + " and (?::text is null or c.payload -> 'issuer' ->> 'value' = ?::text)"
        + " and (?::text is null or c.payload -> 'holders' @> jsonb_build_array(?::text))"
        + " and (?::text is null or exists (select 1 from jsonb_array_elements(jsonb_build_array(c.payload -> 'credentialSubject' -> 'hd') || (c.payload -> 'credentialSubject' -> 'tl')) s"
        + " where s -> 'id' ->> 'value' = ?::text))"
        + " and (?::text is null or exists (select 1 from jsonb_array_elements(jsonb_build_array(c.payload -> 'credentialSubject' -> 'hd') || (c.payload -> 'credentialSubject' -> 'tl')) s,"
        + " jsonb_object_keys(s -> 'claims') k where starts_with(k, ?::text)))"
        + (registered ? " and (?::text is null or r.payload ->> 'registryAdmin' = ?::text)" : "")
        + (singular ? " order by " + (state.equals("all") ? "(a.archived_at_offset is null) desc, " : "")
            + "c.created_at_offset desc, c.created_at_ix desc, c.contract_id desc"
            : " order by c.payload ->> 'id', c.contract_id")
        + " limit ? offset ?";
  }

  private StatementBinder historyBinder(boolean registered,
      io.unlockit.domain.credential.query.CredentialFilters filters, long snapshot,
      String lastId, String lastContract, String credentialId, int limit, long offset) {
    return statement -> {
      int i = 1;
      statement.setString(i++, CREDENTIAL_INTERFACE);
      statement.setLong(i++, snapshot);
      statement.setString(i++, CREDENTIAL_INTERFACE);
      statement.setLong(i++, snapshot);
      if (registered) {
        statement.setString(i++, REGISTERED_CREDENTIAL_INTERFACE);
        statement.setLong(i++, snapshot);
      }
      statement.setString(i++, lastId);
      statement.setString(i++, lastId);
      statement.setString(i++, lastContract);
      for (String value : new String[] {credentialId, filters.createdFrom(), filters.createdUntil(),
          filters.archivedFrom(), filters.archivedUntil(), filters.issuer(), filters.holder(),
          filters.credentialSubjectId(), filters.keyPrefix()}) {
        statement.setString(i++, value);
        statement.setString(i++, value);
      }
      if (registered) {
        statement.setString(i++, filters.registryAdmin());
        statement.setString(i++, filters.registryAdmin());
      }
      statement.setInt(i++, limit);
      statement.setLong(i, offset);
    };
  }

  @Override
  public List<Credential> findCredentialHistory(io.unlockit.domain.credential.query.CredentialFilters filters,
      long snapshot, String lastId, String lastContract, String credentialId, int limit, long offset) {
    return queryCredentials(historySql(false, filters.state(), credentialId != null),
        historyBinder(false, filters, snapshot, lastId, lastContract, credentialId, limit, offset), true);
  }

  @Override
  public List<RegisteredCredential> findRegisteredCredentialHistory(io.unlockit.domain.credential.query.CredentialFilters filters,
      long snapshot, String lastId, String lastContract, String credentialId, int limit, long offset) {
    return queryRegistered(historySql(true, filters.state(), credentialId != null),
        historyBinder(true, filters, snapshot, lastId, lastContract, credentialId, limit, offset), true);
  }

  static io.unlockit.domain.credential.model.CredentialLifecycle mapLifecycle(ResultSet rows) throws SQLException {
    return new io.unlockit.domain.credential.model.CredentialLifecycle(
        rows.getObject("archived_at_offset") == null ? "active" : "archived",
        rows.getString("template_fqn"), rows.getString("payload_type"), number(rows, "create_event_pk"),
        rows.getString("create_event_id"), number(rows, "created_at_ix"), number(rows, "created_at_offset"),
        timestamp(rows, "created_effective_at"), number(rows, "archive_event_pk"), rows.getString("archive_event_id"),
        number(rows, "archived_at_ix"), number(rows, "archived_at_offset"), timestamp(rows, "archived_effective_at"));
  }

  private static Long number(ResultSet rows, String column) throws SQLException {
    long value = rows.getLong(column);
    return rows.wasNull() ? null : value;
  }

  private static String timestamp(ResultSet rows, String column) throws SQLException {
    var value = rows.getTimestamp(column);
    return value == null ? null : value.toInstant().toString();
  }

  @Override
  public void checkCredentialProjections() {
    long snapshot = latestOffset();
    validateOffset(snapshot);
    for (String state : List.of("active", "archived", "all")) {
      var filters = new io.unlockit.domain.credential.query.CredentialFilters(state, null, null, null, null,
          null, null, null, null, null);
      findCredentialHistory(filters, snapshot, null, null, null, 1, 0);
      findRegisteredCredentialHistory(filters, snapshot, null, null, null, 1, 0);
    }
    try (Connection connection = dataSource.getConnection()) {
      checkProjection(connection, CREDENTIAL_INTERFACE);
      checkProjection(connection, REGISTERED_CREDENTIAL_INTERFACE);
      checkProjection(connection, CREDENTIAL_REGISTRY_FACTORY_INTERFACE);
    } catch (SQLException exception) {
      throw new PqsUnavailableException(exception);
    }
  }

  private void checkProjection(Connection connection, String projection) throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement(READINESS_SQL)) {
      statement.setString(1, projection);
      statement.executeQuery();
    }
  }

  private List<Credential> queryCredentials(String sql, StatementBinder binder) {
    return queryCredentials(sql, binder, false);
  }

  private List<Credential> queryCredentials(String sql, StatementBinder binder, boolean history) {
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      binder.bind(statement);
      List<Credential> credentials = new ArrayList<>();
      try (ResultSet rows = statement.executeQuery()) {
        while (rows.next()) {
          Credential credential = mapCredential(rows.getString("contract_id"), rows.getString("credential_payload"));
          credentials.add(history ? credential.withLifecycle(mapLifecycle(rows)) : credential);
        }
      }
      return credentials;
    } catch (SQLException exception) {
      throw new PqsUnavailableException(exception);
    }
  }

  private List<RegisteredCredential> queryRegistered(String sql, StatementBinder binder) {
    return queryRegistered(sql, binder, false);
  }

  private List<RegisteredCredential> queryRegistered(String sql, StatementBinder binder, boolean history) {
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      binder.bind(statement);
      List<RegisteredCredential> credentials = new ArrayList<>();
      try (ResultSet rows = statement.executeQuery()) {
        while (rows.next()) {
          RegisteredCredential credential = mapRegisteredPayloads(rows.getString("contract_id"),
              rows.getString("credential_payload"), rows.getString("registration_payload"));
          credentials.add(history ? new RegisteredCredential(
              credential.credential().withLifecycle(mapLifecycle(rows)), credential.registration()) : credential);
        }
      }
      return credentials;
    } catch (SQLException exception) {
      throw new PqsUnavailableException(exception);
    }
  }

  Credential mapCredential(String contractId, String credentialPayload) throws SQLException {
    try {
      JsonNode credential = objectMapper.readTree(credentialPayload);
      return new Credential(
          contractId,
          text(credential.path("id")),
          credential.path("issuer"),
          credential.path("credentialTypes"),
          subjects(credential.path("credentialSubject")),
          credential.path("holders"),
          credential.path("anchorers"),
          text(credential.path("validFrom")),
          text(credential.path("validUntil")));
    } catch (JsonProcessingException exception) {
      throw new SQLException("PQS returned an unsupported Credential payload", exception);
    }
  }

  RegisteredCredential mapRegisteredPayloads(
      String contractId, String credentialPayload, String registrationPayload) throws SQLException {
    try {
      JsonNode registration = objectMapper.readTree(registrationPayload);
      return new RegisteredCredential(
          mapCredential(contractId, credentialPayload),
          new Registration(
              text(registration.path("registryAdmin")),
              text(registration.path("registeredAt")),
              text(registration.path("expiresAt")),
              registration.path("meta")));
    } catch (JsonProcessingException exception) {
      throw new SQLException("PQS returned an unsupported RegisteredCredential payload", exception);
    }
  }

  CredentialRegistryFactory mapCredentialRegistryFactory(String contractId, String factoryPayload)
      throws SQLException {
    try {
      JsonNode factory = objectMapper.readTree(factoryPayload);
      return new CredentialRegistryFactory(
          contractId, text(factory.path("registryAdmin")), factory.path("anchorers"));
    } catch (JsonProcessingException exception) {
      throw new SQLException(
          "PQS returned an unsupported CredentialRegistryFactory payload", exception);
    }
  }

  private JsonNode subjects(JsonNode node) {
    if (node.isArray()) return node;
    var result = objectMapper.createArrayNode();
    result.add(node.path("hd"));
    node.path("tl").forEach(result::add);
    return result;
  }

  private static String text(JsonNode node) {
    if (node.isMissingNode() || node.isNull()) {
      return null;
    }
    return node.isTextual() ? node.textValue() : node.toString();
  }

  @FunctionalInterface
  private interface StatementBinder {
    void bind(PreparedStatement statement) throws SQLException;
  }
}
