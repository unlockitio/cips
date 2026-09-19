package org.cantonnetwork.credentials;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.javalin.Javalin;
import io.javalin.http.HttpStatus;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Main {
  private Main() {}

  public static void main(String[] args) {
    ObjectMapper mapper = new ObjectMapper();
    HikariDataSource dataSource = dataSource();
    CredentialRepository repository = new CredentialRepository(dataSource, mapper);
    int port = Integer.parseInt(env("API_PORT", "8080"));

    Javalin app = Javalin.create(config -> config.jsonMapper(new io.javalin.json.JavalinJackson(mapper, false)));
    app.get("/health/live", context -> context.json(Map.of("status", "UP")));
    app.get("/health/ready", context -> {
      try {
        repository.checkReady();
        context.json(Map.of("status", "UP"));
      } catch (SQLException exception) {
        context.status(HttpStatus.SERVICE_UNAVAILABLE)
            .json(Map.of("status", "DOWN", "reason", safeMessage(exception)));
      }
    });
    app.get("/api/v1/credentials/{credentialId}", context -> {
      List<CredentialResponse> matches = repository.findByCredentialId(context.pathParam("credentialId"));
      if (matches.isEmpty()) {
        context.status(HttpStatus.NOT_FOUND).json(Map.of("error", "credential not found"));
      } else if (matches.size() > 1) {
        context.status(HttpStatus.CONFLICT).json(Map.of("error", "duplicate logical credential ID"));
      } else {
        context.json(matches.getFirst());
      }
    });
    app.get("/api/v1/credentials", context -> {
      PageRequest request = PageRequest.parse(context.queryParam("page"), context.queryParam("pageSize"));
      List<CredentialResponse> rows = repository.page(request.offset(), request.pageSize() + 1);
      boolean hasNext = rows.size() > request.pageSize();
      List<CredentialResponse> items = hasNext ? rows.subList(0, request.pageSize()) : rows;
      Map<String, Object> response = new LinkedHashMap<>();
      response.put("items", items);
      response.put("page", request.page());
      response.put("pageSize", request.pageSize());
      response.put("hasNext", hasNext);
      context.json(response);
    });
    app.exception(IllegalArgumentException.class, (exception, context) ->
        context.status(HttpStatus.BAD_REQUEST).json(Map.of("error", exception.getMessage())));
    app.exception(SQLException.class, (exception, context) ->
        context.status(HttpStatus.SERVICE_UNAVAILABLE)
            .json(Map.of("error", "PQS query failed", "reason", safeMessage(exception))));
    app.events(events -> events.serverStopped(dataSource::close));
    app.start(port);
  }

  private static HikariDataSource dataSource() {
    HikariConfig config = new HikariConfig();
    config.setJdbcUrl(env("PQS_JDBC_URL", "jdbc:postgresql://localhost:5432/pqs"));
    config.setUsername(env("PQS_DB_USER", "pqs"));
    config.setPassword(env("PQS_DB_PASSWORD", "pqs-demo"));
    config.setMaximumPoolSize(4);
    config.setInitializationFailTimeout(-1);
    return new HikariDataSource(config);
  }

  private static String env(String name, String fallback) {
    return System.getenv().getOrDefault(name, fallback);
  }

  private static String safeMessage(Exception exception) {
    return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
  }
}
