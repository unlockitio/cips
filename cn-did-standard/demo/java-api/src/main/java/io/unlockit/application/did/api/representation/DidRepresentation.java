package io.unlockit.application.did.api.representation;

public record DidRepresentation(
        String contractId,
        String did,
        DidDocumentRepresentation didDocument) {}
