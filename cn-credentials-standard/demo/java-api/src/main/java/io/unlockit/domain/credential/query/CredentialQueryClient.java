package io.unlockit.domain.credential.query;

import io.unlockit.domain.credential.model.Credential;
import io.unlockit.domain.credential.model.CredentialRegistryFactory;
import io.unlockit.domain.credential.model.RegisteredCredential;
import io.unlockit.application.credential.dto.CredentialTypeItem;
import java.util.List;

public interface CredentialQueryClient {
  List<Credential> findCredentialById(String credentialId);

  List<Credential> findActiveNames(String name, String party);

  List<Credential> findCredentialPage(long offset, int limit);

  List<RegisteredCredential> findRegisteredCredentialById(String credentialId);

  List<RegisteredCredential> findRegisteredCredentialPage(long offset, int limit);

  List<CredentialTypeItem> findCredentialTypes(List<String> issuers, String registryAdmin,
      String state, long offset, int limit);

  List<String> findCredentialRegistryIds(long offset, int limit);

  List<CredentialRegistryFactory> findCredentialRegistryFactories(String registryId);

  long latestOffset();

  void validateOffset(long offset);

  List<Credential> findCredentialHistory(CredentialFilters filters, long snapshot,
      String lastId, String lastContract, String credentialId, int limit, long offset);

  List<RegisteredCredential> findRegisteredCredentialHistory(CredentialFilters filters, long snapshot,
      String lastId, String lastContract, String credentialId, int limit, long offset);

  void checkCredentialProjections();
}
