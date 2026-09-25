package io.unlockit.application.did.manager;

import io.unlockit.application.did.api.DidPathCodec;
import io.unlockit.domain.did.entity.IntrinsicDid;
import io.unlockit.domain.did.entity.RegisteredDid;
import io.unlockit.domain.did.repository.DidRepository;
import io.unlockit.domain.did.value_object.DidValue;
import io.unlockit.domain.did.value_object.PartyId;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;

@ApplicationScoped
public class DidManager {
    static final int DEFAULT_PAGE_SIZE = 50;
    static final int MAX_PAGE_SIZE = 100;
    private final DidRepository repository;

    public DidManager(DidRepository repository) {
        this.repository = repository;
    }

    public Page<IntrinsicDid> listDids(String pageValue, String pageSizeValue) {
        PageRequest page = pageRequest(pageValue, pageSizeValue);
        return page(repository.findIntrinsicPage(page.offset(), page.fetchSize()), page);
    }

    @jakarta.inject.Inject
    @org.eclipse.microprofile.config.inject.ConfigProperty(name = "did.bootstrap.dso-did")
    java.util.Optional<String> configuredDso = java.util.Optional.empty();

    public IntrinsicDid resolveDso() {
        var matches = configuredDso.isPresent()
                ? repository.findIntrinsicByDid(new DidValue(configuredDso.get())).stream().toList()
                : repository.findDsoCandidates();
        if (matches.size() != 1) {
            throw new IllegalStateException("Exactly one DSO candidate is required for bootstrap");
        }
        return matches.getFirst();
    }

    public IntrinsicDid resolve(String encodedDidSegment) {
        DidValue did = decode(encodedDidSegment);
        return repository.findIntrinsicByDid(did).orElseThrow(() -> new DidNotFoundException(did));
    }

    public Page<RegisteredDid> listRegisteredDids(String partyId, String pageValue, String pageSizeValue) {
        PageRequest page = pageRequest(pageValue, pageSizeValue);
        List<RegisteredDid> rows = partyId == null
                ? repository.findRegisteredPage(page.offset(), page.fetchSize())
                : repository.findRegisteredByPartyId(new PartyId(partyId), page.offset(), page.fetchSize());
        return page(rows, page);
    }

    public RegisteredDid resolveRegistered(String encodedDidSegment) {
        DidValue did = decode(encodedDidSegment);
        return repository.findRegisteredByDid(did).orElseThrow(() -> new DidNotFoundException(did));
    }

    private static DidValue decode(String encodedDidSegment) {
        return new DidValue(DidPathCodec.decodeCanonicalSegment(encodedDidSegment));
    }

    private static <T> Page<T> page(List<T> rows, PageRequest page) {
        return new Page<>(rows.stream().limit(page.pageSize()).toList(), page.page(), page.pageSize(),
                rows.size() > page.pageSize());
    }

    private static PageRequest pageRequest(String pageValue, String pageSizeValue) {
        long page = parseLong(pageValue, 0, "page");
        int pageSize = parseInt(pageSizeValue, DEFAULT_PAGE_SIZE, "pageSize");
        if (page < 0) throw new IllegalArgumentException("page must be at least 0");
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE)
            throw new IllegalArgumentException("pageSize must be between 1 and 100");
        try {
            return new PageRequest(page, pageSize, Math.multiplyExact(page, pageSize));
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("pagination offset overflows", exception);
        }
    }

    private static long parseLong(String value, long defaultValue, String name) {
        if (value == null) return defaultValue;
        try { return Long.parseLong(value); }
        catch (NumberFormatException exception) { throw new IllegalArgumentException(name + " must be an integer", exception); }
    }

    private static int parseInt(String value, int defaultValue, String name) {
        if (value == null) return defaultValue;
        try { return Integer.parseInt(value); }
        catch (NumberFormatException exception) { throw new IllegalArgumentException(name + " must be an integer", exception); }
    }

    public record Page<T>(List<T> items, long page, int pageSize, boolean hasNext) {}
    private record PageRequest(long page, int pageSize, long offset) { int fetchSize() { return pageSize + 1; } }

    public static final class DidNotFoundException extends RuntimeException {
        public DidNotFoundException(DidValue did) { super("DID not found: " + did); }
    }
}
