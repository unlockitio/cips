package io.unlockit.domain.credential.did;

import java.util.List;

/**
 * Reads self-claimed name aliases from the separate DID demo. An assertion is a
 * discovery hint only; the credential registry remains the allocation authority.
 */
public interface DidNameAssertionClient {
  boolean enabled();

  List<DidNameAssertion> findByAlias(String network, String name);

  List<DidNameAssertion> findBySubject(String party);

  record DidNameAssertion(String did, String alias, String subject) {}
}
