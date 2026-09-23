package io.unlockit.application.credential.dto;

import java.util.List;

public record RegisteredCredentialPageResponse(
    List<RegisteredCredentialResponse> items, long page, int pageSize, boolean hasNext) {}
