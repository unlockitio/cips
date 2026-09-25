package io.unlockit.domain.did;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.unlockit.domain.did.value_object.DidValue;
import org.junit.jupiter.api.Test;

class DidValueTest {
    @Test
    void preservesValidBaseDid() {
        assertEquals("did:Example:Alice-01", new DidValue("did:Example:Alice-01").value());
    }

    @Test
    void rejectsInvalidOrDidUrlValues() {
        for (String value : new String[] {"", " ", "did:example", "http:example:alice", "did:example:a/b", "did:example:a?x", "did:example:a#k"}) {
            assertThrows(IllegalArgumentException.class, () -> new DidValue(value), value);
        }
    }
}
