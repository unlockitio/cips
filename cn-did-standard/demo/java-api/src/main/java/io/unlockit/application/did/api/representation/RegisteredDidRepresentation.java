package io.unlockit.application.did.api.representation;

public record RegisteredDidRepresentation(
        String contractId,
        String did,
        DidDocumentRepresentation didDocument,
        DidRegistrationRepresentation registration) {}
