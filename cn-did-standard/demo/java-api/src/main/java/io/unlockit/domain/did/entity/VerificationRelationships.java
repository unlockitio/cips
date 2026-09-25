package io.unlockit.domain.did.entity;

import java.util.List;

public record VerificationRelationships(
        List<String> authentication,
        List<String> assertionMethod,
        List<String> capabilityInvocation,
        List<String> capabilityDelegation,
        List<String> keyAgreement) {
    public VerificationRelationships {
        authentication = copy(authentication);
        assertionMethod = copy(assertionMethod);
        capabilityInvocation = copy(capabilityInvocation);
        capabilityDelegation = copy(capabilityDelegation);
        keyAgreement = copy(keyAgreement);
    }

    private static List<String> copy(List<String> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    public List<String> allReferences() {
        return java.util.stream.Stream.of(authentication, assertionMethod, capabilityInvocation, capabilityDelegation, keyAgreement)
                .flatMap(List::stream)
                .toList();
    }
}
