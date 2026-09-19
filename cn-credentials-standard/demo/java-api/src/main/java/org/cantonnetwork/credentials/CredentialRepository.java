package org.cantonnetwork.credentials;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;

public final class CredentialRepository {
  private static final String CREDENTIAL =
      "canton-network-credentials-interfaces:Canton.Network.Credentials.V1:Credential";
  private static final String REGISTERED =
      "canton-network-credentials-interfaces:Canton.Network.Credentials.V1:RegisteredCredential";
  private final DataSource dataSource;
  private final ObjectMapper mapper;

  public CredentialRepository(DataSource dataSource, ObjectMapper mapper) {
    this.dataSource = dataSource;
    this.mapper = mapper;
  }

  public void checkReady() throws SQLException {
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement("select 1 from active(?) limit 1")) {
      statement.setString(1, REGISTERED);
      statement.executeQuery();
    }
  }

  public List<CredentialResponse> findByCredentialId(String credentialId) throws SQLException {
    String sql = """
        select r.contract_id, c.payload as credential_payload, r.payload as registration_payload
          from active(?) r join active(?) c using (contract_id)
         where c.payload ->> 'id' = ?
         order by r.contract_id
         limit 2
        """;
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, REGISTERED);
      statement.setString(2, CREDENTIAL);
      statement.setString(3, credentialId);
      return read(statement);
    }
  }

  public List<CredentialResponse> page(long offset, int limit) throws SQLException {
    String sql = """
        select r.contract_id, c.payload as credential_payload, r.payload as registration_payload
          from active(?) r join active(?) c using (contract_id)
         order by c.payload ->> 'id', r.contract_id
         limit ? offset ?
        """;
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, REGISTERED);
      statement.setString(2, CREDENTIAL);
      statement.setInt(3, limit);
      statement.setLong(4, offset);
      return read(statement);
    }
  }

  private List<CredentialResponse> read(PreparedStatement statement) throws SQLException {
    List<CredentialResponse> result = new ArrayList<>();
    try (ResultSet rows = statement.executeQuery()) {
      while (rows.next()) {
        try {
          result.add(map(
              rows.getString("contract_id"),
              mapper.readTree(rows.getString("credential_payload")),
              mapper.readTree(rows.getString("registration_payload"))));
        } catch (Exception exception) {
          throw new SQLException("PQS returned an unsupported interface payload", exception);
        }
      }
    }
    return result;
  }

  static CredentialResponse map(String contractId, JsonNode credential, JsonNode registered) {
    JsonNode registration = registered.path("registration");
    return new CredentialResponse(
        contractId,
        text(credential.path("id")),
        credential.path("issuer"),
        text(registration.path("registryAdmin")),
        credential.path("credentialTypes"),
        credential.path("credentialSubject"),
        credential.path("holders"),
        text(credential.path("validFrom")),
        text(credential.path("validUntil")));
  }

  private static String text(JsonNode node) {
    if (node.isMissingNode() || node.isNull()) return null;
    if (node.isTextual()) return node.textValue();
    return node.toString();
  }
}
