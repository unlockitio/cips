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
    manager = new CredentialManager(new CredentialListingManager(queryClient, new CredentialResponseMapper(),
        new ObjectMapper(), "test-credentials-secret-at-least-32-bytes", 900));
    var json = new ObjectMapper().createObjectNode();
    credential = new Credential(
        "contract", "credential", json, json, json, json, json, null, null);
  }

  private static io.unlockit.domain.credential.query.CredentialFilters filters() {
    return new io.unlockit.domain.credential.query.CredentialFilters(null, null, null, null, null, null, null, null, null, null);
  }

  @Test
  void returnsExactlyOneCredential() {
    when(queryClient.findCredentialHistory(filters(), 0, null, null, "credential", 2, 0)).thenReturn(List.of(credential));
    assertEquals("contract", manager.findByCredentialId("credential").contractId());
  }

  @Test
  void rejectsNoCredential() {
    when(queryClient.findCredentialHistory(filters(), 0, null, null, "missing", 2, 0)).thenReturn(List.of());
    assertThrows(CredentialNotFoundException.class, () -> manager.findByCredentialId("missing"));
  }

  @Test
  void rejectsDuplicateCredentials() {
    when(queryClient.findCredentialHistory(filters(), 0, null, null, "duplicate", 2, 0)).thenReturn(List.of(credential, credential));
    assertThrows(DuplicateCredentialException.class, () -> manager.findByCredentialId("duplicate"));
  }

  @Test
  void appliesDefaultsAndFetchesOneExtraRow() {
    when(queryClient.findCredentialHistory(filters(), 0, null, null, null, 51, 0)).thenReturn(List.of(credential));
    var page = manager.findPage(null, null);
    assertEquals(0, page.page());
    assertEquals(50, page.pageSize());
    assertEquals(false, page.hasNext());
    verify(queryClient).findCredentialHistory(filters(), 0, null, null, null, 51, 0);
  }

  @Test
  void reportsNextPageAndTrimsExtraRow() {
    when(queryClient.findCredentialHistory(filters(), 0, null, null, null, 101, 200)).thenReturn(java.util.Collections.nCopies(101, credential));
    var page = manager.findPage("2", "100");
    assertEquals(100, page.items().size());
    assertEquals(true, page.hasNext());
  }

  @Test
  void rejectsUnbackedLifecycleScopesAndHistoryWithoutQueryingPqs() {
    for (var scope : List.of("inactive", "unknown")) {
      assertThrows(InvalidPaginationException.class,
          () -> manager.findByCredentialId("credential", scope));
      assertThrows(InvalidPaginationException.class,
          () -> manager.findRegisteredByCredentialId("credential", scope));
      assertThrows(InvalidPaginationException.class,
          () -> manager.findPage(null, null, scope));
      assertThrows(InvalidPaginationException.class,
          () -> manager.findRegisteredPage(null, null, scope));
    }
    assertThrows(io.unlockit.domain.credential.exception.UnsupportedCapabilityException.class,
        manager::rejectHistory);
    org.mockito.Mockito.verifyNoInteractions(queryClient);
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
