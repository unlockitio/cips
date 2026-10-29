package io.unlockit.application.credential.dto;

import java.util.List;

public record CredentialTypeCollection(List<CredentialTypeItem> items, long page, int pageSize,
    boolean hasNext) {}
