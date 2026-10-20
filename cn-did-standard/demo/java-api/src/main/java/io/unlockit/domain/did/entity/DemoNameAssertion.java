package io.unlockit.domain.did.entity;

/**
 * A self-claimed demo name alias read from the concrete demo template. It is a
 * discovery hint, never an allocation: several documents may claim the same
 * alias, and a claim may name a Party the claimant does not control.
 */
public record DemoNameAssertion(String did, String alias, String subject) {
    public DemoNameAssertion {
        if (did == null || did.isBlank() || alias == null || alias.isBlank()
                || subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("DID, alias, and subject are required");
        }
    }
}
