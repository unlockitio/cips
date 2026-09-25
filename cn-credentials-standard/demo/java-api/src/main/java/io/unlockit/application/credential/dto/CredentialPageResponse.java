package io.unlockit.application.credential.dto;

import java.util.List;

public record CredentialPageResponse(
    List<CredentialResponse> items, long page, int pageSize, boolean hasNext,
    String snapshotOffset, String nextPageToken) {
  public CredentialPageResponse(List<CredentialResponse> items, long page, int pageSize, boolean hasNext) {
    this(items, page, pageSize, hasNext, null, null);
  }
}
