package io.unlockit.domain.did.value_object;

import java.util.Objects;
import java.util.regex.Pattern;

public record DidValue(String value) implements Comparable<DidValue> {
    private static final Pattern BASE_DID = Pattern.compile("did:[A-Za-z0-9]+:[^\\s/?#]+", Pattern.UNICODE_CHARACTER_CLASS);

    public DidValue {
        Objects.requireNonNull(value, "DID is required");
        if (!BASE_DID.matcher(value).matches()) {
            throw new IllegalArgumentException("DID must be a nonblank base DID in did:<method>:<method-specific-id> form");
        }
    }

    @Override
    public int compareTo(DidValue other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
