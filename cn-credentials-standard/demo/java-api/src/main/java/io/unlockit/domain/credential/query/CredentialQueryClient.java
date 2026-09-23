package io.unlockit.domain.credential.query;

import io.unlockit.domain.credential.model.Credential;
import io.unlockit.domain.credential.model.RegisteredCredential;
import java.util.List;

public interface CredentialQueryClient {
  List<Credential> findCredentialById(String credentialId);

  List<Credential> findCredentialPage(long offset, int limit);

  List<RegisteredCredential> findRegisteredCredentialById(String credentialId);

  List<RegisteredCredential> findRegisteredCredentialPage(long offset, int limit);

  void checkCredentialProjections();
}
