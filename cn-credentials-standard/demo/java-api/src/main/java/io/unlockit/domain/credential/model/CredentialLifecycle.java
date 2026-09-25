package io.unlockit.domain.credential.model;

public record CredentialLifecycle(
    String state, String templateFqn, String payloadType,
    Long createEventPk, String createEventId, Long createdAtIx, Long createdAtOffset,
    String createdEffectiveAt, Long archiveEventPk, String archiveEventId,
    Long archivedAtIx, Long archivedAtOffset, String archivedEffectiveAt) {}
