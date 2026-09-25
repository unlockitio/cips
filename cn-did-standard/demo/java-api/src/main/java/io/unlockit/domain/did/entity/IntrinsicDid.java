package io.unlockit.domain.did.entity;

import io.unlockit.domain.did.value_object.DidValue;

public record IntrinsicDid(String contractId, DidValue id, DidDocument document) {
    public IntrinsicDid {
        if (contractId == null || contractId.isBlank() || id == null || document == null) {
            throw new IllegalArgumentException("Contract ID, DID, and document are required");
        }
        if (!id.equals(document.id())) {
            throw new IllegalArgumentException("DID aggregate and document IDs must match");
        }
    }
}
