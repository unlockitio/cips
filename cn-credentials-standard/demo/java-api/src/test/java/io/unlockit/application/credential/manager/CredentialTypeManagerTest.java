package io.unlockit.application.credential.manager;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.unlockit.application.credential.dto.CredentialTypeItem;
import io.unlockit.domain.credential.exception.InvalidPaginationException;
import io.unlockit.domain.credential.query.CredentialQueryClient;
import java.util.List;
import org.junit.jupiter.api.Test;

class CredentialTypeManagerTest {
  private final CredentialQueryClient client = mock(CredentialQueryClient.class);
  private final CredentialTypeManager manager = new CredentialTypeManager(client);

  @Test
  void defaultsToCurrentActiveViewAndOmitsCountsAndTokens() {
    var item = new CredentialTypeItem("issuer", "VerifiableCredential", "2026-01-01T00:00:00Z",
        "2026-01-02T00:00:00Z");
    when(client.findCredentialTypes(List.of(), null, "active", 0, 51)).thenReturn(List.of(item));
    var result = manager.list(null, null, null, null, null);
    assertEquals(50, result.pageSize());
    assertEquals(0, result.page());
    assertEquals(List.of(item), result.items());
    assertFalse(result.hasNext());
    var json = new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(result);
    assertFalse(json.has("snapshotOffset"));
    assertFalse(json.has("nextPageToken"));
    assertFalse(json.at("/items/0").has("credentialCount"));
  }

  @Test
  void looksAheadWithFiltersAndOffsetWithoutCarryingSnapshotAcrossPages() {
    var first = new CredentialTypeItem("a", "A", "t1", "t2");
    var second = new CredentialTypeItem("b", "B", "t3", "t4");
    when(client.findCredentialTypes(List.of("a", "b"), "admin", "archived", 2, 2))
        .thenReturn(List.of(first, second));
    var page = manager.list(List.of("a", "b"), "admin", "archived", "2", "1");
    assertEquals(List.of(first), page.items());
    assertTrue(page.hasNext());
    assertEquals(2, page.page());
    verify(client).findCredentialTypes(List.of("a", "b"), "admin", "archived", 2, 2);
    when(client.findCredentialTypes(List.of("a", "b"), "admin", "all", 1, 2))
        .thenReturn(List.of());
    assertTrue(manager.list(List.of("a", "b"), "admin", "all", "1", "1").items().isEmpty());
    verify(client).findCredentialTypes(List.of("a", "b"), "admin", "all", 1, 2);
  }

  @Test
  void rejectsInvalidFiltersAndPaginationWithoutQuerying() {
    for (String state : List.of("", "inactive", "ACTIVE"))
      assertThrows(InvalidPaginationException.class, () -> manager.list(null, null, state, null, null));
    assertThrows(InvalidPaginationException.class, () -> manager.list(List.of(""), null, null, null, null));
    assertThrows(InvalidPaginationException.class, () -> manager.list(null, "", null, null, null));
    for (String page : List.of("-1", "bad", "9223372036854775807"))
      assertThrows(InvalidPaginationException.class, () -> manager.list(null, null, null, page, "100"));
    for (String size : List.of("0", "101", "bad"))
      assertThrows(InvalidPaginationException.class, () -> manager.list(null, null, null, null, size));
    verifyNoInteractions(client);
  }
}
