package io.unlockit.application.credential.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

public record CredentialRegistryResponse(
    String registryId,
    String apiVersion,
    FamilyCapabilities credentials,
    FamilyCapabilities registeredCredentials,
    List<IssuanceFactoryResponse> issuanceFactories) {
  public record FamilyCapabilities(
      boolean activeRecords,
      boolean inactiveRecords,
      boolean eventHistory,
      String[] supportedLifecycleScopes,
      String[] supportedFilters,
      int defaultPageSize,
      int maximumPageSize,
      @JsonInclude(JsonInclude.Include.NON_NULL) CredentialTypeAggregationCapability credentialTypeAggregation) {
    public FamilyCapabilities(boolean activeRecords, boolean inactiveRecords, boolean eventHistory,
        String[] supportedLifecycleScopes, String[] supportedFilters, int defaultPageSize,
        int maximumPageSize) {
      this(activeRecords, inactiveRecords, eventHistory, supportedLifecycleScopes, supportedFilters,
          defaultPageSize, maximumPageSize, null);
    }
  }

  public record CredentialTypeAggregationCapability(boolean supported, String[] supportedFilters,
      boolean credentialCount) {}
}
