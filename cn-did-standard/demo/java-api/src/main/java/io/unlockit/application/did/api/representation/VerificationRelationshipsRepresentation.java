package io.unlockit.application.did.api.representation;

import java.util.List;

public record VerificationRelationshipsRepresentation(
        List<String> authentication,
        List<String> assertionMethod,
        List<String> capabilityInvocation,
        List<String> capabilityDelegation,
        List<String> keyAgreement) {}
