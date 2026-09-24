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
       order by f.payload ->> 'issuer', f.contract_id
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
  public void checkCredentialProjections() {
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
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      binder.bind(statement);
      List<Credential> credentials = new ArrayList<>();
      try (ResultSet rows = statement.executeQuery()) {
        while (rows.next()) {
          credentials.add(
              mapCredential(rows.getString("contract_id"), rows.getString("credential_payload")));
        }
      }
      return credentials;
    } catch (SQLException exception) {
      throw new PqsUnavailableException(exception);
    }
  }

  private List<RegisteredCredential> queryRegistered(String sql, StatementBinder binder) {
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      binder.bind(statement);
      List<RegisteredCredential> credentials = new ArrayList<>();
      try (ResultSet rows = statement.executeQuery()) {
        while (rows.next()) {
          credentials.add(
              mapRegisteredPayloads(
                  rows.getString("contract_id"),
                  rows.getString("credential_payload"),
                  rows.getString("registration_payload")));
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
          credential.path("credentialSubject"),
          credential.path("holders"),
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
          contractId, text(factory.path("registryAdmin")), text(factory.path("issuer")));
    } catch (JsonProcessingException exception) {
      throw new SQLException(
          "PQS returned an unsupported CredentialRegistryFactory payload", exception);
    }
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
