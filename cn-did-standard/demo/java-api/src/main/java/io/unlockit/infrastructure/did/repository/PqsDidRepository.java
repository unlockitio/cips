package io.unlockit.infrastructure.did.repository;

import io.unlockit.domain.did.entity.IntrinsicDid;
import io.unlockit.domain.did.entity.RegisteredDid;
import io.unlockit.domain.did.exception.DuplicateDidException;
import io.unlockit.domain.did.repository.DidRepository;
import io.unlockit.domain.did.value_object.DidValue;
import io.unlockit.domain.did.value_object.PartyId;
import io.unlockit.infrastructure.did.pqs.PqsDidQueryClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class PqsDidRepository implements DidRepository {
    private final PqsDidQueryClient queryClient;

    @Inject
    public PqsDidRepository(PqsDidQueryClient queryClient) {
        this.queryClient = queryClient;
    }

    @Override
    public List<IntrinsicDid> findDsoCandidates() { return queryClient.findDsoCandidates(); }

    @Override
    public List<IntrinsicDid> findIntrinsicPage(long offset, int limit) {
        return queryClient.findIntrinsicPage(offset, limit);
    }

    @Override
    public Optional<IntrinsicDid> findIntrinsicByDid(DidValue did) {
        return requireAtMostOne(queryClient.findIntrinsicByDid(did.value()), did.value());
    }

    @Override
    public List<RegisteredDid> findRegisteredPage(long offset, int limit) {
        return queryClient.findRegisteredPage(offset, limit);
    }

    @Override
    public Optional<RegisteredDid> findRegisteredByDid(DidValue did) {
        return requireAtMostOne(queryClient.findRegisteredByDid(did.value()), did.value());
    }

    @Override
    public List<RegisteredDid> findRegisteredByPartyId(PartyId partyId, long offset, int limit) {
        return queryClient.findRegisteredByPartyId(partyId.value(), offset, limit);
    }

    private static <T> Optional<T> requireAtMostOne(List<T> matches, String did) {
        if (matches.size() > 1) throw new DuplicateDidException(did);
        return matches.stream().findFirst();
    }
}
