package io.unlockit.application.credential.dto;

import java.util.List;

public record CredentialRegistryPageResponse(
    List<CredentialRegistryResponse> items, long page, int pageSize, boolean hasNext) {}
