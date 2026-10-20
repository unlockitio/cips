package io.unlockit.application.credential.manager;

import com.fasterxml.jackson.databind.JsonNode;
import io.unlockit.application.credential.dto.ResolutionResult;
import io.unlockit.domain.credential.did.DidNameAssertionClient;
import io.unlockit.domain.credential.exception.CredentialNotFoundException;
import io.unlockit.domain.credential.exception.DidAssertionConflictException;
import io.unlockit.domain.credential.exception.InvalidPaginationException;
import io.unlockit.domain.credential.exception.UnsupportedCapabilityException;
import io.unlockit.domain.credential.model.Credential;
import io.unlockit.domain.credential.query.CredentialQueryClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class NameResolutionManager {
  private final CredentialQueryClient client;
  private final DidNameAssertionClient dids;
  private final String network;
  private final Clock clock;

  @Inject
  public NameResolutionManager(CredentialQueryClient client, DidNameAssertionClient dids,
      @ConfigProperty(name = "credentials.resolution.network") String network) {
    this(client, dids, network, Clock.systemUTC());
  }

  NameResolutionManager(CredentialQueryClient client, DidNameAssertionClient dids, String network, Clock clock) {
    if (!java.util.Set.of("mainnet", "testnet", "devnet", "localnet").contains(network))
      throw new IllegalArgumentException("credentials.resolution.network must be a CIP #171 network");
    this.client = client;
    this.dids = dids;
    this.network = network;
    this.clock = clock;
  }

  public ResolutionResult resolve(String name, String requestedNetwork, String source) {
    validate(requestedNetwork, source);
    if (!validName(name)) throw new InvalidPaginationException("name must be printable non-whitespace ASCII");
    var matches = results(client.findActiveNames(name, null), name, null);
    if ("did".equals(source)) return resolveWithDidAssertion(name, matches);
    if (matches.isEmpty()) throw new CredentialNotFoundException(name);
    if (matches.size() != 1) throw new io.unlockit.domain.credential.exception.DuplicateCredentialException(name);
    return matches.getFirst();
  }

  public List<ResolutionResult> reverseResolve(String party, String requestedNetwork, String source) {
    validate(requestedNetwork, source);
    if (party == null || party.isBlank()) throw new InvalidPaginationException("party is required");
    var matches = results(client.findActiveNames(null, party), null, party);
    var names = new HashSet<String>();
    for (var match : matches) {
      if (!names.add(match.fqpn()))
        throw new io.unlockit.domain.credential.exception.DuplicateCredentialException(match.fqpn());
    }
    if ("did".equals(source)) return reverseResolveWithDidAssertion(party, matches);
    return matches;
  }

  /**
   * A DID alias is a self-claim, so it only ever narrows the registry answer. The
   * registry allocation decides the result: a claim naming a different Party is a
   * conflict, an unallocated name is not found, and conflicting duplicate claims
   * are a conflict rather than an arbitrary pick.
   */
  private ResolutionResult resolveWithDidAssertion(String name, List<ResolutionResult> allocated) {
    var assertions = dids.findByAlias(network, name);
    var asserted = assertions.stream().map(DidNameAssertionClient.DidNameAssertion::subject)
        .filter(subject -> subject != null && !subject.isBlank()).distinct().toList();
    if (asserted.isEmpty())
      throw new CredentialNotFoundException("no DID asserts the name " + name);
    if (asserted.size() != 1)
      throw new DidAssertionConflictException("conflicting DID aliases assert the name " + name);
    if (allocated.isEmpty()) throw new CredentialNotFoundException(name);
    if (allocated.size() != 1)
      throw new io.unlockit.domain.credential.exception.DuplicateCredentialException(name);
    var allocation = allocated.getFirst();
    if (!allocation.partyId().equals(asserted.getFirst()))
      throw new DidAssertionConflictException(
          "DID asserts " + name + " for " + asserted.getFirst() + " but it is allocated to " + allocation.partyId());
    return allocation;
  }

  /**
   * Reverse keeps the same agreement rule: a name is returned only when the
   * registry allocation for the Party and a DID self-claim for that exact Party
   * agree. Claims about other Parties never add or replace names.
   */
  private List<ResolutionResult> reverseResolveWithDidAssertion(String party, List<ResolutionResult> allocated) {
    var assertions = dids.findBySubject(party);
    var asserted = new HashSet<String>();
    for (var assertion : assertions) {
      if (party.equals(assertion.subject()) && assertion.alias() != null) asserted.add(assertion.alias());
    }
    return allocated.stream().filter(result -> asserted.contains("urn:fqpn:" + result.fqpn())).toList();
  }

  private void validate(String requestedNetwork, String source) {
    if (requestedNetwork == null || requestedNetwork.isBlank() || !requestedNetwork.equals(network))
      throw new InvalidPaginationException("network must match the configured network");
    if (source != null && !source.equals("credential") && !source.equals("did"))
      throw new InvalidPaginationException("source must be credential or did");
    if ("did".equals(source) && !dids.enabled())
      throw new UnsupportedCapabilityException("DID name resolution is not configured for this deployment");
  }

  private List<ResolutionResult> results(List<Credential> credentials, String requestedName, String requestedParty) {
    var results = new ArrayList<ResolutionResult>();
    var now = clock.instant();
    for (var credential : credentials) {
      if (!validAt(credential, now) || credential.subjects().size() != 1) continue;
      JsonNode subject = credential.subjects().get(0);
      JsonNode id = subject.path("id");
      if (!"W3C_VC_Identifier_Party".equals(id.path("tag").asText())) continue;
      String party = id.path("value").asText(null);
      JsonNode claim = subject.path("claims").path("demo.example/name");
      if (!"AV_Text".equals(claim.path("tag").asText())) continue;
      String name = claim.path("value").asText(null);
      if (party == null || party.isBlank() || !validName(name)
          || (requestedName != null && !requestedName.equals(name))
          || (requestedParty != null && !requestedParty.equals(party))) continue;
      results.add(new ResolutionResult(network + ":demo:" + name, party, name, null,
          credential.validUntil()));
    }
    return results;
  }

  private static boolean validAt(Credential credential, Instant now) {
    try {
      return (credential.validFrom() == null || !Instant.parse(credential.validFrom()).isAfter(now))
          && (credential.validUntil() == null || Instant.parse(credential.validUntil()).isAfter(now));
    } catch (RuntimeException malformed) {
      return false;
    }
  }

  private static boolean validName(String name) {
    if (name == null || name.isEmpty()) return false;
    for (int i = 0; i < name.length(); i++)
      if (name.charAt(i) < 33 || name.charAt(i) > 126) return false;
    return true;
  }
}
