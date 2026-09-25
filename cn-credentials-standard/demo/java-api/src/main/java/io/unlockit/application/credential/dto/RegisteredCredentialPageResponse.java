package io.unlockit.application.credential.dto;

import java.util.List;

public record RegisteredCredentialPageResponse(
    List<RegisteredCredentialResponse> items, long page, int pageSize, boolean hasNext,
    String snapshotOffset, String nextPageToken) {
  public RegisteredCredentialPageResponse(List<RegisteredCredentialResponse> items, long page, int pageSize, boolean hasNext) {
    this(items, page, pageSize, hasNext, null, null);
  }
}
