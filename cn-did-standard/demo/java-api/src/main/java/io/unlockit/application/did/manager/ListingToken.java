package io.unlockit.application.did.manager;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

@ApplicationScoped
public class ListingToken {
    private final ObjectMapper mapper;
    private final byte[] secret;
    private final long ttl;
    private final Clock clock;

    @Inject
    public ListingToken(ObjectMapper mapper,
            @ConfigProperty(name = "did.listing.hmac-secret") String secret,
            @ConfigProperty(name = "did.listing.token-ttl-seconds", defaultValue = "900") long ttl) {
        this(mapper, secret, ttl, Clock.systemUTC());
    }

    ListingToken(ObjectMapper mapper, String secret, long ttl, Clock clock) {
        this.mapper = mapper;
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        if (this.secret.length < 32 || ttl < 1) throw new IllegalArgumentException("Listing HMAC secret requires at least 32 bytes and positive TTL");
        this.ttl = ttl;
        this.clock = clock;
    }

    public long expiry() { return Math.addExact(clock.instant().getEpochSecond(), ttl); }

    public String encode(Cursor cursor) {
        try {
            String body = Base64.getUrlEncoder().withoutPadding().encodeToString(mapper.writeValueAsBytes(cursor));
            return body + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(sign(body));
        } catch (Exception e) { throw new IllegalStateException("Cannot sign listing token", e); }
    }

    public Cursor decode(String token) {
        try {
            if (token.length() > 16384) throw new IllegalArgumentException();
            String[] parts = token.split("\\.", -1);
            if (parts.length != 2) throw new IllegalArgumentException();
            byte[] signature = Base64.getUrlDecoder().decode(parts[1]);
            if (!MessageDigest.isEqual(sign(parts[0]), signature)
                    || !Base64.getUrlEncoder().withoutPadding().encodeToString(signature).equals(parts[1]))
                throw new IllegalArgumentException();
            Cursor cursor = mapper.readValue(Base64.getUrlDecoder().decode(parts[0]), Cursor.class);
            if (cursor.version() != 1) throw new IllegalArgumentException();
            if (cursor.expiresAt() <= clock.instant().getEpochSecond()) throw new ListingException("token_expired");
            return cursor;
        } catch (ListingException e) { throw e; }
        catch (Exception e) { throw new ListingException("invalid_token"); }
    }

    private byte[] sign(String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret, "HmacSHA256"));
        return mac.doFinal(body.getBytes(StandardCharsets.US_ASCII));
    }

    public record Cursor(int version, String resource, DidListingManager.Filters filters, int pageSize,
            long snapshotOffset, long page, String lastDid, String lastContractId, long expiresAt) {}

    public static class ListingException extends IllegalArgumentException {
        public ListingException(String code) { super(code); }
    }
}
