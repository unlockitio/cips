package io.unlockit.application.credential.manager;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.unlockit.application.credential.mapper.CredentialResponseMapper;
import io.unlockit.domain.credential.exception.*;
import io.unlockit.domain.credential.model.*;
import io.unlockit.domain.credential.query.*;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CredentialListingManagerTest {
  CredentialQueryClient client;
  CredentialListingManager manager;
  CredentialFilters filters = new CredentialFilters(null, null, null, null, null, null, null, null, null, null);
  Credential first = new Credential("c1", "id1", null, null, null, null, null, null, null);
  Credential second = new Credential("c2", "id2", null, null, null, null, null, null, null);

  @BeforeEach void setup() {
    client = mock(CredentialQueryClient.class);
    manager = new CredentialListingManager(client, new CredentialResponseMapper(), new ObjectMapper(),
        "test-credentials-secret-at-least-32-bytes", 900);
    when(client.latestOffset()).thenReturn(9007199254740993L);
    when(client.findCredentialHistory(any(), anyLong(), any(), any(), any(), anyInt(), anyLong()))
        .thenReturn(List.of(first, second));
  }

  @Test void continuationUsesKeysetAndJumpUsesSameSnapshot() {
    var page = manager.list(null, "1", null, filters);
    assertEquals("9007199254740993", page.snapshotOffset());
    assertTrue(page.hasNext());
    assertEquals(1, page.items().size());
    manager.list(null, "1", page.nextPageToken(), filters);
    verify(client).findCredentialHistory(filters, 9007199254740993L, "id1", "c1", null, 2, 0);
    manager.list("7", "1", page.nextPageToken(), filters);
    verify(client).findCredentialHistory(filters, 9007199254740993L, null, null, null, 2, 7);
    verify(client, times(1)).latestOffset();
    verify(client, times(3)).validateOffset(9007199254740993L);
  }

  @Test void freshPageChoosesFreshSnapshot() {
    manager.list("3", "2", null, filters);
    verify(client).findCredentialHistory(filters, 9007199254740993L, null, null, null, 3, 6);
    verify(client).latestOffset();
  }

  @Test void emptyPageHasNoToken() {
    when(client.findCredentialHistory(any(), anyLong(), any(), any(), any(), anyInt(), anyLong())).thenReturn(List.of());
    var page = manager.list(null, null, null, filters);
    assertFalse(page.hasNext());
    assertNull(page.nextPageToken());
  }

  @Test void tokensBindResourceFiltersAndSize() {
    var page = manager.list(null, "1", null, filters);
    assertThrows(InvalidPaginationException.class, () -> manager.listRegistered(null, "1", page.nextPageToken(), filters));
    assertThrows(InvalidPaginationException.class, () -> manager.list(null, "2", page.nextPageToken(), filters));
    var other = new CredentialFilters("all", null, null, null, null, null, null, null, null, null);
    assertThrows(InvalidPaginationException.class, () -> manager.list(null, "1", page.nextPageToken(), other));
  }

  @Test void invalidPaginationDoesNotReachPqs() {
    for (String page : List.of("-1", "wrong", "9223372036854775807"))
      assertThrows(InvalidPaginationException.class, () -> manager.list(page, "50", null, filters));
    for (String size : List.of("0", "101", "wrong"))
      assertThrows(InvalidPaginationException.class, () -> manager.list(null, size, null, filters));
    verifyNoInteractions(client);
  }

  @Test void singularDuplicateActiveIsConflictAndArchivedUsesLatest() {
    assertThrows(DuplicateCredentialException.class, () -> manager.get("id1", "active"));
    when(client.findCredentialHistory(any(), anyLong(), any(), any(), any(), eq(1), anyLong())).thenReturn(List.of(first));
    assertEquals("c1", manager.get("id1", "archived").contractId());
    assertThrows(DuplicateCredentialException.class, () -> manager.get("id1", "all"));
    when(client.findCredentialHistory(eq(filters), anyLong(), any(), any(), any(), eq(2), anyLong())).thenReturn(List.of());
    assertEquals("c1", manager.get("id1", "all").contractId());
    when(client.findCredentialHistory(any(), anyLong(), any(), any(), any(), anyInt(), anyLong())).thenReturn(List.of());
    assertThrows(CredentialNotFoundException.class, () -> manager.get("missing", "active"));
  }

  @Test void registeredPaginationAndSingularUseRegisteredQuery() {
    var registration = new Registration("admin", null, null, null);
    when(client.findRegisteredCredentialHistory(any(), anyLong(), any(), any(), any(), anyInt(), anyLong()))
        .thenReturn(List.of(new RegisteredCredential(first, registration), new RegisteredCredential(second, registration)));
    var page = manager.listRegistered(null, "1", null, filters);
    assertEquals("admin", page.items().getFirst().registration().registryAdmin());
    manager.listRegistered(null, "1", page.nextPageToken(), filters);
    verify(client).findRegisteredCredentialHistory(filters, 9007199254740993L, "id1", "c1", null, 2, 0);
    assertThrows(DuplicateCredentialException.class, () -> manager.getRegistered("id1", "active"));
  }

  @Test void timeFiltersValidateAndNormalize() {
    var normalized = new CredentialFilters("all", "2026-01-01T01:00:00+01:00", "2026-01-02T00:00:00Z",
        null, null, null, null, null, null, null);
    assertEquals("2026-01-01T00:00:00Z", normalized.createdFrom());
    assertThrows(InvalidPaginationException.class, () -> new CredentialFilters("inactive", null, null, null, null, null, null, null, null, null));
    assertThrows(InvalidPaginationException.class, () -> new CredentialFilters(null, "bad", null, null, null, null, null, null, null, null));
    assertThrows(InvalidPaginationException.class, () -> new CredentialFilters(null, "2026-01-01T00:00:00Z", "2026-01-01T00:00:00Z", null, null, null, null, null, null, null));
  }
}
