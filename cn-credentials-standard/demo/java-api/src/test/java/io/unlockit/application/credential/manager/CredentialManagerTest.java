package io.unlockit.application.credential.manager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.unlockit.application.credential.mapper.CredentialResponseMapper;
import io.unlockit.domain.credential.exception.CredentialNotFoundException;
import io.unlockit.domain.credential.exception.DuplicateCredentialException;
import io.unlockit.domain.credential.exception.InvalidPaginationException;
import io.unlockit.domain.credential.model.Credential;
import io.unlockit.domain.credential.query.CredentialQueryClient;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CredentialManagerTest {
  @Mock CredentialQueryClient queryClient;
  private CredentialManager manager;
  private Credential credential;

  @BeforeEach
  void setUp() {
    manager = new CredentialManager(queryClient, new CredentialResponseMapper());
    var json = new ObjectMapper().createObjectNode();
    credential = new Credential(
        "contract", "credential", json, json, json, json, null, null);
  }

  @Test
  void returnsExactlyOneCredential() {
    when(queryClient.findCredentialById("credential")).thenReturn(List.of(credential));
    assertEquals("contract", manager.findByCredentialId("credential").contractId());
  }

  @Test
  void rejectsNoCredential() {
    when(queryClient.findCredentialById("missing")).thenReturn(List.of());
    assertThrows(CredentialNotFoundException.class, () -> manager.findByCredentialId("missing"));
  }

  @Test
  void rejectsDuplicateCredentials() {
    when(queryClient.findCredentialById("duplicate")).thenReturn(List.of(credential, credential));
    assertThrows(DuplicateCredentialException.class, () -> manager.findByCredentialId("duplicate"));
  }

  @Test
  void appliesDefaultsAndFetchesOneExtraRow() {
    when(queryClient.findCredentialPage(0, 51)).thenReturn(List.of(credential));
    var page = manager.findPage(null, null);
    assertEquals(0, page.page());
    assertEquals(50, page.pageSize());
    assertEquals(false, page.hasNext());
    verify(queryClient).findCredentialPage(0, 51);
  }

  @Test
  void reportsNextPageAndTrimsExtraRow() {
    when(queryClient.findCredentialPage(200, 101)).thenReturn(java.util.Collections.nCopies(101, credential));
    var page = manager.findPage("2", "100");
    assertEquals(100, page.items().size());
    assertEquals(true, page.hasNext());
  }

  @Test
  void rejectsInvalidPaginationAndOverflow() {
    assertThrows(InvalidPaginationException.class, () -> manager.findPage("-1", "50"));
    assertThrows(InvalidPaginationException.class, () -> manager.findPage("0", "0"));
    assertThrows(InvalidPaginationException.class, () -> manager.findPage("0", "101"));
    assertThrows(InvalidPaginationException.class, () -> manager.findPage("bogus", "50"));
    assertThrows(InvalidPaginationException.class, () -> manager.findPage("922337203685477581", "100"));
  }
}
