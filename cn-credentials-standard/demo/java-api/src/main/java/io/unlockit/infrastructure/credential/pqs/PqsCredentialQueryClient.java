package io.unlockit.infrastructure.credential.pqs;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agroal.api.AgroalDataSource;
import io.unlockit.domain.credential.exception.PqsUnavailableException;
import io.unlockit.domain.credential.model.Credential;
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
  static final String READINESS_SQL =
      "select 1 from active(?) c join active(?) r using (contract_id) limit 1";

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
  public void checkCredentialProjections() {
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(READINESS_SQL)) {
      statement.setString(1, CREDENTIAL_INTERFACE);
      statement.setString(2, REGISTERED_CREDENTIAL_INTERFACE);
      statement.executeQuery();
    } catch (SQLException exception) {
      throw new PqsUnavailableException(exception);
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
