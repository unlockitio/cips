package io.unlockit.application.did.api.representation;

import java.util.List;

public record DidDocumentRepresentation(
        String id,
        List<String> controller,
        List<VerificationMethodRepresentation> verificationMethod,
        VerificationRelationshipsRepresentation verificationRelationships,
        List<ServiceRepresentation> service) {}
