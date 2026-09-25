package io.unlockit.domain.did.repository;

import io.unlockit.domain.did.entity.IntrinsicDid;
import io.unlockit.domain.did.entity.RegisteredDid;
import io.unlockit.domain.did.value_object.DidValue;
import io.unlockit.domain.did.value_object.PartyId;
import java.util.List;
import java.util.Optional;

public interface DidRepository {
    List<IntrinsicDid> findIntrinsicPage(long offset, int limit);

    default List<IntrinsicDid> findDsoCandidates() {
        java.util.ArrayList<IntrinsicDid> matches = new java.util.ArrayList<>();
        for (long offset = 0; ; offset += 100) {
            var rows = findIntrinsicPage(offset, 100);
            rows.stream().filter(d -> d.id().value().startsWith("did:canton:DSO::")).forEach(matches::add);
            if (matches.size() > 1 || rows.size() < 100) return matches;
        }
    }

    Optional<IntrinsicDid> findIntrinsicByDid(DidValue did);

    List<RegisteredDid> findRegisteredPage(long offset, int limit);

    Optional<RegisteredDid> findRegisteredByDid(DidValue did);

    List<RegisteredDid> findRegisteredByPartyId(PartyId partyId, long offset, int limit);
}
