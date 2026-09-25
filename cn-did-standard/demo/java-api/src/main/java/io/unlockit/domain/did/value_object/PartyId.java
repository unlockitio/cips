package io.unlockit.domain.did.value_object;

import java.util.Objects;

public record PartyId(String value) {
    public PartyId {
        Objects.requireNonNull(value, "Party ID is required");
        if (value.isBlank() || !value.equals(value.trim()) || value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Party ID must be nonblank and contain no surrounding whitespace or control characters");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
