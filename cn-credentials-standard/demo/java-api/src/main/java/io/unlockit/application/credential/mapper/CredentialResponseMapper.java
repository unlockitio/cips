package io.unlockit.application.credential.mapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.unlockit.application.credential.dto.CredentialProjection;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import io.unlockit.application.credential.dto.CredentialResponse;
import io.unlockit.application.credential.dto.RegisteredCredentialResponse;
import io.unlockit.application.credential.dto.RegistrationResponse;
import io.unlockit.domain.credential.model.Credential;
import io.unlockit.domain.credential.model.RegisteredCredential;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class CredentialResponseMapper {
  public CredentialResponse toResponse(Credential credential) {
    return new CredentialResponse(
        credential.contractId(), credential.credentialId(), state(credential), projection(credential),
        roles(credential), credential.lifecycle());
  }

  public RegisteredCredentialResponse toResponse(RegisteredCredential registeredCredential) {
    var credential = registeredCredential.credential();
    var registration = registeredCredential.registration();
    return new RegisteredCredentialResponse(
        credential.contractId(),
        credential.credentialId(),
        state(credential),
        projection(credential),
        new RegistrationResponse(
            registration.registryAdmin(),
            registration.registeredAt(),
            registration.expiresAt(),
            registration.meta()), roles(credential), credential.lifecycle());
  }

  private static String state(Credential credential) {
    return credential.lifecycle() == null ? "active" : credential.lifecycle().state();
  }

  private static final JsonNodeFactory JSON = JsonNodeFactory.instance;
  private static final String CONTEXT = "https://www.w3.org/ns/credentials/v2";
  private static final Set<String> RESERVED = Set.of("id", "type", "@context");

  private static CredentialProjection projection(Credential credential) {
    ArrayNode context = JSON.arrayNode().add(CONTEXT);
    JsonNode types = credential.credentialTypes();
    if (types == null || !types.isArray() || types.isEmpty()) {
      throw invalid("credentialTypes must be a nonempty array including VerifiableCredential");
    }
    ArrayNode projectedTypes = JSON.arrayNode();
    boolean vc = false;
    for (JsonNode type : types) {
      if (!type.isTextual() || type.asText().isBlank()) throw invalid("invalid credential type");
      projectedTypes.add(type.asText());
      vc |= type.asText().equals("VerifiableCredential");
    }
    if (!vc) throw invalid("credentialTypes must include VerifiableCredential");
    JsonNode subjects = credential.subjects();
    if (subjects == null || !subjects.isArray() || subjects.isEmpty()) throw invalid("missing subjects");
    ArrayNode projectedSubjects = JSON.arrayNode();
    for (JsonNode subject : subjects) {
      if (!subject.isObject() || !subject.path("claims").isObject()) throw invalid("invalid subject");
      ObjectNode projected = JSON.objectNode();
      JsonNode id = subject.get("id");
      if (id != null && !id.isNull()) projected.put("id", identifier(id));
      subject.path("claims").fields().forEachRemaining(entry -> {
        if (RESERVED.contains(entry.getKey())) throw invalid("reserved claim key: " + entry.getKey());
        projected.set(entry.getKey(), value(entry.getValue()));
      });
      projectedSubjects.add(projected);
    }
    return new CredentialProjection(context, credential.credentialId(), projectedTypes,
        identifier(credential.issuer()), projectedSubjects, credential.validFrom(), credential.validUntil());
  }

  private static ObjectNode roles(Credential credential) {
    ObjectNode roles = JSON.objectNode();
    if (credential.holders() == null || !credential.holders().isArray()) throw invalid("invalid holders");
    ArrayNode holders = JSON.arrayNode();
    Set<String> seen = new HashSet<>();
    for (JsonNode holder : credential.holders()) {
      String party = text(holder);
      if (!seen.add(party)) throw invalid("duplicate holder");
      holders.add(party);
    }
    JsonNode anchorers = credential.anchorers();
    if (anchorers == null || !anchorers.isObject() || !anchorers.path("tl").isArray()) throw invalid("invalid anchorers");
    ArrayNode anchors = JSON.arrayNode();
    seen.clear();
    String first = text(anchorers.get("hd"));
    seen.add(first);
    anchors.add(first);
    for (JsonNode anchor : anchorers.path("tl")) {
      String party = text(anchor);
      if (!seen.add(party)) throw invalid("duplicate anchorer");
      anchors.add(party);
    }
    roles.set("holders", holders);
    roles.set("anchorers", anchors);
    return roles;
  }

  private static String identifier(JsonNode node) {
    if (node == null || !node.isObject() || node.size() != 2 || !node.path("tag").isTextual()
        || !Set.of("W3C_VC_Identifier", "W3C_VC_Identifier_Party").contains(node.path("tag").asText())) {
      throw invalid("invalid identifier");
    }
    return text(node.get("value"));
  }

  private static String text(JsonNode node) {
    if (node == null || !node.isTextual() || node.asText().isBlank()) throw invalid("expected nonempty text");
    return node.asText();
  }

  private static JsonNode value(JsonNode node) {
    if (node == null || !node.isObject() || node.size() != 2 || !node.path("tag").isTextual()
        || !node.has("value")) throw invalid("invalid AnyValue");
    String tag = node.path("tag").asText();
    JsonNode raw = node.get("value");
    try {
      return switch (tag) {
        case "AV_Text", "AV_Party", "AV_ContractId" -> JSON.textNode(text(raw));
        case "AV_Int" -> JSON.numberNode(new BigInteger(numberText(raw)));
        case "AV_Decimal" -> JSON.numberNode(new BigDecimal(numberText(raw)));
        case "AV_Bool" -> {
          if (!raw.isBoolean()) throw invalid("invalid AV_Bool");
          yield JSON.booleanNode(raw.booleanValue());
        }
        case "AV_Date" -> JSON.textNode(LocalDate.parse(text(raw)).toString());
        case "AV_Time" -> JSON.textNode(Instant.parse(text(raw)).toString());
        case "AV_RelTime" -> {
          // LF RelTime is serialized as a microsecond count by the Ledger JSON API.
          yield JSON.textNode(Duration.ofNanos(new BigInteger(numberText(raw)).multiply(BigInteger.valueOf(1000)).longValueExact()).toString());
        }
        case "AV_List" -> {
          if (!raw.isArray()) throw invalid("invalid AV_List");
          ArrayNode list = JSON.arrayNode();
          raw.forEach(item -> list.add(value(item)));
          yield list;
        }
        case "AV_Map" -> {
          if (!raw.isObject()) throw invalid("invalid AV_Map");
          ObjectNode map = JSON.objectNode();
          raw.fields().forEachRemaining(entry -> map.set(entry.getKey(), value(entry.getValue())));
          yield map;
        }
        default -> throw invalid("unknown AnyValue tag: " + tag);
      };
    } catch (java.time.format.DateTimeParseException | ArithmeticException | NumberFormatException e) {
      throw new IllegalArgumentException("invalid " + tag + " value", e);
    }
  }

  private static String numberText(JsonNode raw) {
    if (raw == null || !(raw.isTextual() || raw.isNumber())) throw invalid("invalid numeric AnyValue");
    return raw.asText();
  }

  private static IllegalArgumentException invalid(String reason) {
    return new IllegalArgumentException("Invalid LF credential projection: " + reason);
  }
}
