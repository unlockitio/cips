package io.unlockit.application.credential.dto;

public record RegistryInfoResponse(
    String apiVersion,
    FamilyCapabilities credentials,
    FamilyCapabilities registeredCredentials) {
  public record FamilyCapabilities(
      boolean activeRecords,
      boolean inactiveRecords,
      boolean eventHistory,
      String[] supportedLifecycleScopes,
      String[] supportedFilters,
      int defaultPageSize,
      int maximumPageSize) {}
}
