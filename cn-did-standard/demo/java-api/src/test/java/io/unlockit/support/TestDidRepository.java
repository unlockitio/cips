package io.unlockit.support;

import io.unlockit.domain.did.entity.IntrinsicDid;
import io.unlockit.domain.did.entity.RegisteredDid;
import io.unlockit.domain.did.repository.DidRepository;
import io.unlockit.domain.did.value_object.DidValue;
import io.unlockit.domain.did.value_object.PartyId;
import java.util.List;
import java.util.Optional;

public class TestDidRepository implements DidRepository {
    private final List<IntrinsicDid> intrinsic = DidTestData.intrinsicFixtures();
    private final List<RegisteredDid> registered = DidTestData.registeredFixtures();

    @Override public List<IntrinsicDid> findIntrinsicPage(long offset, int limit) {
        return intrinsic.stream().skip(offset).limit(limit).toList();
    }
    @Override public Optional<IntrinsicDid> findIntrinsicByDid(DidValue did) {
        return intrinsic.stream().filter(value -> value.id().equals(did)).findFirst();
    }
    @Override public List<RegisteredDid> findRegisteredPage(long offset, int limit) {
        return registered.stream().skip(offset).limit(limit).toList();
    }
    @Override public Optional<RegisteredDid> findRegisteredByDid(DidValue did) {
        return registered.stream().filter(value -> value.intrinsic().id().equals(did)).findFirst();
    }
    @Override public List<RegisteredDid> findRegisteredByPartyId(PartyId partyId, long offset, int limit) {
        return registered.stream().filter(value -> value.partyId().filter(partyId::equals).isPresent())
                .skip(offset).limit(limit).toList();
    }
}
