package io.unlockit.application.bft.api;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

public final class DidPathCodec {
    private DidPathCodec() {}

    public static String decodeCanonicalSegment(String segment) {
        if (segment == null || segment.isBlank()) throw new IllegalArgumentException("Encoded DID path segment is required");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        for (int i = 0; i < segment.length();) {
            char value = segment.charAt(i);
            if (value == '%') {
                if (i + 2 >= segment.length()) throw new IllegalArgumentException("Malformed percent escape in DID path segment");
                int high = Character.digit(segment.charAt(i + 1), 16);
                int low = Character.digit(segment.charAt(i + 2), 16);
                if (high < 0 || low < 0) throw new IllegalArgumentException("Malformed percent escape in DID path segment");
                bytes.write((high << 4) + low);
                i += 3;
            } else {
                if (value > 0x7f) throw new IllegalArgumentException("DID path segment must use canonical UTF-8 percent encoding");
                bytes.write(value);
                i++;
            }
        }
        try {
            String decoded = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes.toByteArray())).toString();
            if (decoded.indexOf('/') >= 0 || decoded.indexOf('?') >= 0 || decoded.indexOf('#') >= 0)
                throw new IllegalArgumentException("DID path must identify a base DID in one segment");
            if (!encodeCanonicalSegment(decoded).equals(segment))
                throw new IllegalArgumentException("DID path segment is not canonically encoded");
            return decoded;
        } catch (CharacterCodingException exception) {
            throw new IllegalArgumentException("DID path segment is not valid UTF-8", exception);
        }
    }

    public static String encodeCanonicalSegment(String did) {
        StringBuilder encoded = new StringBuilder();
        for (byte value : did.getBytes(StandardCharsets.UTF_8)) {
            int unsigned = value & 0xff;
            if (unsigned >= 'A' && unsigned <= 'Z' || unsigned >= 'a' && unsigned <= 'z'
                    || unsigned >= '0' && unsigned <= '9' || unsigned == '-' || unsigned == '.' || unsigned == '_' || unsigned == '~') {
                encoded.append((char) unsigned);
            } else {
                encoded.append('%').append(Character.toUpperCase(Character.forDigit(unsigned >>> 4, 16)))
                        .append(Character.toUpperCase(Character.forDigit(unsigned & 0xf, 16)));
            }
        }
        return encoded.toString();
    }
}
