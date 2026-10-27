package io.unlockit.application.credential.mapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.unlockit.application.credential.dto.VerificationResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.Signature;
import java.security.spec.NamedParameterSpec;
import java.security.spec.EdECPublicKeySpec;
import java.security.spec.EdECPoint;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import org.erdtman.jcs.JsonCanonicalizer;

/** Offline, deliberately narrow demo trust profile; did:key is the only locally resolvable controller. */
public final class OfflineCredentialVerifier {
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final String SCHEMA_ID = "https://example.org/demo/achievement-schema.json";
  private static final String SCHEMA = "{\"$id\":\"https://example.org/demo/achievement-schema.json\",\"$schema\":\"https://json-schema.org/draft/2020-12/schema\",\"properties\":{\"achievement\":{\"type\":\"object\"}},\"required\":[\"achievement\"],\"type\":\"object\"}\n";
  private final Clock clock;

  public OfflineCredentialVerifier(Clock clock) { this.clock = clock; }

  public VerificationResponse verify(byte[] secured, String mechanism) {
    var errors = new ArrayList<String>();
    var warnings = new ArrayList<String>();
    warnings.add("Cryptographic proof under demo did:key only; issuer trust and factual truth are not assessed");
    String controller = null;
    String status = "INDETERMINATE";
    JsonNode document = JSON.createObjectNode();
    String compact = new String(secured, StandardCharsets.UTF_8);
    if ("application/vc".equals(mechanism)) {
      try { document = JSON.readTree(secured); }
      catch (Exception e) { errors.add("Malformed secured JSON document"); status = "FAILED"; }
    }
    try {
      if ("application/vc+jwt".equals(mechanism)) {
        String[] parts = compact.split("\\.", -1);
        if (parts.length != 3) throw new IllegalArgumentException("Malformed compact JWS");
        JsonNode header = JSON.readTree(decodeUrl(parts[0]));
        document = JSON.readTree(decodeUrl(parts[1]));
        if (!header.isObject() || !document.isObject()) throw new IllegalArgumentException("JWS header and payload must be JSON objects");
        if (!"EdDSA".equals(header.path("alg").asText())) {
          warnings.add("Unsupported JWS algorithm");
        } else if (!"vc+jwt".equals(header.path("typ").asText())
            || (header.has("cty") && !"vc".equals(header.path("cty").asText()))) {
          errors.add("Invalid JWS VC media headers");
          status = "FAILED";
        } else if (document.has("vc") || document.has("vp") || document.has("proof") || header.has("crit")
            || header.has("b64") || header.has("jwk") || header.has("jku") || header.has("x5u")) {
          errors.add("Unexpected JOSE or JWT credential claims");
        } else {
          controller = controller(header.path("kid").asText(), document.path("issuer").asText());
          if (!ed25519(header.path("kid").asText(), (parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII), decodeUrl(parts[2])))
            errors.add("Invalid JWS signature");
          status = errors.isEmpty() ? "VERIFIED" : "FAILED";
        }
      } else if ("application/vc".equals(mechanism) && errors.isEmpty()) {
        JsonNode proof = document.path("proof");
        if (!document.isObject()) throw new IllegalArgumentException("Secured document must be a JSON object");
        if (!proof.isObject() || !"DataIntegrityProof".equals(proof.path("type").asText())
            || !"eddsa-jcs-2022".equals(proof.path("cryptosuite").asText())
            || !"assertionMethod".equals(proof.path("proofPurpose").asText())) {
          warnings.add("Unsupported Data Integrity proof");
        } else {
          controller = controller(proof.path("verificationMethod").asText(), document.path("issuer").asText());
          ObjectNode config = ((ObjectNode) proof).deepCopy();
          config.remove("proofValue");
          config.set("@context", document.path("@context"));
          ObjectNode unsecured = ((ObjectNode) document).deepCopy();
          unsecured.remove("proof");
          byte[] input = concat(sha(canonical(config)), sha(canonical(unsecured)));
          if (!ed25519(proof.path("verificationMethod").asText(), input, decode58(proof.path("proofValue").asText())))
            errors.add("Invalid Data Integrity signature");
          checkTime(proof.path("created"), proof.path("expires"), errors);
          status = errors.isEmpty() ? "VERIFIED" : "FAILED";
        }
      } else {
        warnings.add("Unsupported or absent securing mechanism");
      }
      if (status.equals("VERIFIED") || (status.equals("FAILED") && document.isObject() && document.size() > 0)) {
        if (!document.path("@context").isArray() || !"https://www.w3.org/ns/credentials/v2".equals(document.path("@context").path(0).asText())
            || !document.path("type").isArray() || !arrayContains(document.path("type"), "VerifiableCredential"))
          errors.add("Invalid VC context or type");
        checkTime(document.path("validFrom"), document.path("validUntil"), errors);
        if (!errors.isEmpty()) status = "FAILED";
      }
    } catch (UnsupportedOperationException e) {
      warnings.add(e.getMessage());
      status = "INDETERMINATE";
    } catch (Exception e) {
      errors.add("Malformed or invalid secured credential: " + e.getMessage());
      status = "FAILED";
    }
    return new VerificationResponse(mechanism, status, clock.instant(), controller, errors, warnings,
        schema(document));
  }

  private VerificationResponse.SchemaValidation schema(JsonNode document) {
    JsonNode refs = document == null ? null : document.path("credentialSchema");
    if (refs == null || !refs.isArray() || refs.isEmpty())
      return new VerificationResponse.SchemaValidation("Indeterminate", List.of("No local credential schema"));
    boolean matched = false;
    for (JsonNode ref : refs) {
      if (!SCHEMA_ID.equals(ref.path("id").asText()) || !"JsonSchema".equals(ref.path("type").asText()))
        return new VerificationResponse.SchemaValidation("Indeterminate", List.of("Schema is not available locally"));
      matched = true;
      String sri = "sha256-" + Base64.getEncoder().encodeToString(sha(SCHEMA.getBytes(StandardCharsets.UTF_8)));
      if (!sri.equals(ref.path("digestSRI").asText()))
        return new VerificationResponse.SchemaValidation("Failure", List.of("Schema digestSRI mismatch"));
    }
    if (matched) {
      JsonNode subjects = document.path("credentialSubject");
      if (!subjects.isArray() || subjects.isEmpty())
        return new VerificationResponse.SchemaValidation("Failure", List.of("Missing credential subjects"));
      for (JsonNode subject : subjects) if (!subject.path("achievement").isObject())
        return new VerificationResponse.SchemaValidation("Failure", List.of("achievement must be an object"));
      return new VerificationResponse.SchemaValidation("Success", List.of());
    }
    return new VerificationResponse.SchemaValidation("Indeterminate", List.of("No schema evaluated"));
  }

  private void checkTime(JsonNode from, JsonNode until, List<String> errors) {
    try {
      Instant now = clock.instant();
      if (from.isTextual() && now.isBefore(Instant.parse(from.asText()))) errors.add("Credential or proof not yet valid");
      if (until.isTextual() && !now.isBefore(Instant.parse(until.asText()))) errors.add("Credential or proof expired");
    } catch (java.time.format.DateTimeParseException e) { errors.add("Invalid validity timestamp"); }
  }

  private static boolean arrayContains(JsonNode array, String text) {
    for (JsonNode item : array) if (text.equals(item.asText())) return true;
    return false;
  }

  private static String controller(String vm, String issuer) {
    int hash = vm.indexOf('#');
    if (hash < 0 || !vm.startsWith("did:key:z") || !vm.substring(hash + 1).equals(vm.substring("did:key:".length(), hash)))
      throw new UnsupportedOperationException("Only did:key Ed25519 assertionMethod keys are supported");
    String did = vm.substring(0, hash);
    if (!did.equals(issuer)) throw new IllegalArgumentException("Issuer does not control verification method");
    return did;
  }

  private static boolean ed25519(String vm, byte[] input, byte[] signature) throws Exception {
    byte[] multicodec = decode58(vm.substring("did:key:".length(), vm.indexOf('#')));
    if (multicodec.length != 34 || (multicodec[0] & 255) != 0xed || multicodec[1] != 1)
      throw new UnsupportedOperationException("Unsupported did:key codec");
    byte[] y = Arrays.copyOfRange(multicodec, 2, 34);
    boolean xOdd = (y[31] & 0x80) != 0;
    y[31] &= 0x7f;
    for (int i = 0; i < 16; i++) { byte t = y[i]; y[i] = y[31 - i]; y[31 - i] = t; }
    var key = KeyFactory.getInstance("Ed25519").generatePublic(new EdECPublicKeySpec(
        NamedParameterSpec.ED25519, new EdECPoint(xOdd, new java.math.BigInteger(1, y))));
    var verifier = Signature.getInstance("Ed25519");
    verifier.initVerify(key);
    verifier.update(input);
    return verifier.verify(signature);
  }

  private static byte[] decodeUrl(String text) { return Base64.getUrlDecoder().decode(text); }
  private static byte[] decode58(String multibase) {
    if (!multibase.startsWith("z")) throw new IllegalArgumentException("Expected base58btc multibase");
    String alphabet = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";
    java.math.BigInteger number = java.math.BigInteger.ZERO;
    for (char c : multibase.substring(1).toCharArray()) {
      int digit = alphabet.indexOf(c);
      if (digit < 0) throw new IllegalArgumentException("Invalid base58btc");
      number = number.multiply(java.math.BigInteger.valueOf(58)).add(java.math.BigInteger.valueOf(digit));
    }
    byte[] raw = number.equals(java.math.BigInteger.ZERO) ? new byte[0] : number.toByteArray();
    if (raw.length > 0 && raw[0] == 0) raw = Arrays.copyOfRange(raw, 1, raw.length);
    int zeros = 0;
    while (zeros + 1 < multibase.length() && multibase.charAt(zeros + 1) == '1') zeros++;
    return concat(new byte[zeros], raw);
  }
  private static byte[] canonical(JsonNode node) throws Exception {
    return new JsonCanonicalizer(JSON.writeValueAsBytes(node)).getEncodedUTF8();
  }
  private static byte[] sha(byte[] bytes) {
    try { return MessageDigest.getInstance("SHA-256").digest(bytes); }
    catch (Exception e) { throw new IllegalStateException(e); }
  }
  private static byte[] concat(byte[] first, byte[] second) {
    byte[] result = Arrays.copyOf(first, first.length + second.length);
    System.arraycopy(second, 0, result, first.length, second.length);
    return result;
  }
}
