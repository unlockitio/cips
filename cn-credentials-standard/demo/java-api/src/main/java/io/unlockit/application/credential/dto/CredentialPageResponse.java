package io.unlockit.application.credential.dto;

import java.util.List;

public record CredentialPageResponse(
    List<CredentialResponse> items, long page, int pageSize, boolean hasNext) {}
