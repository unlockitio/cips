package io.unlockit.application.did.api.representation;

public record VerificationMethodRepresentation(String id, String type, String controller, String publicKey) {}
