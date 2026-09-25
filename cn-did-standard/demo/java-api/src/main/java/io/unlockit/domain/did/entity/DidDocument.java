package io.unlockit.domain.did.entity;

import io.unlockit.domain.did.value_object.DidValue;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public record DidDocument(
        DidValue id,
        List<DidValue> controller,
        List<VerificationMethod> verificationMethod,
        VerificationRelationships verificationRelationships,
        List<ServiceEntry> service) {
    public DidDocument {
        if (id == null) {
            throw new IllegalArgumentException("DID document ID is required");
        }
        controller = controller == null ? List.of() : List.copyOf(controller);
        if (controller.isEmpty()) {
            throw new IllegalArgumentException("DID document requires at least one controller");
        }
        verificationMethod = verificationMethod == null ? List.of() : List.copyOf(verificationMethod);
        verificationRelationships = verificationRelationships == null
                ? new VerificationRelationships(null, null, null, null, null)
                : verificationRelationships;
        service = service == null ? List.of() : List.copyOf(service);

        Set<String> methodIds = uniqueIds(verificationMethod.stream().map(VerificationMethod::id).toList(), "verification method");
        uniqueIds(service.stream().map(ServiceEntry::id).toList(), "service");
        for (String reference : verificationRelationships.allReferences()) {
            if (reference.startsWith("#") && !methodIds.contains(id.value() + reference)) {
                throw new IllegalArgumentException("Unresolved local verification relationship reference: " + reference);
            }
            if (reference.startsWith(id.value() + "#") && !methodIds.contains(reference)) {
                throw new IllegalArgumentException("Unresolved local verification relationship reference: " + reference);
            }
        }
    }

    private static Set<String> uniqueIds(List<String> ids, String kind) {
        Set<String> unique = new HashSet<>();
        for (String value : ids) {
            if (!unique.add(value)) {
                throw new IllegalArgumentException("Duplicate " + kind + " ID: " + value);
            }
        }
        return Set.copyOf(unique);
    }
}
