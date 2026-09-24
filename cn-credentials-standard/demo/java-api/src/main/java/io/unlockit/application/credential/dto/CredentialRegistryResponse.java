package io.unlockit.application.credential.dto;

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
      int maximumPageSize) {}
}
