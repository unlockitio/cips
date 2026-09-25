package io.unlockit.application.did.api.representation;

public record ErrorRepresentation(int status, String error, String message) {}
