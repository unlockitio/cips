package io.unlockit.domain.did.entity;

import io.unlockit.domain.did.value_object.DidValue;
import java.util.Objects;

public record VerificationMethod(String id, String type, DidValue controller, String publicKey) {
    public VerificationMethod {
        requireText(id, "Verification method ID");
        requireText(type, "Verification method type");
        Objects.requireNonNull(controller, "Verification method controller is required");
        requireText(publicKey, "Verification method public key is required");
    }

    private static void requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required");
        }
    }
}
