package io.unlockit.application.credential.manager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.unlockit.domain.credential.exception.CredentialRegistryNotFoundException;
import io.unlockit.domain.credential.exception.InvalidPaginationException;
import io.unlockit.domain.credential.model.CredentialRegistryFactory;
import io.unlockit.domain.credential.query.CredentialQueryClient;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CredentialRegistryManagerTest {
  @Mock CredentialQueryClient queryClient;
  private CredentialRegistryManager manager;

  @BeforeEach
  void setUp() {
    manager = new CredentialRegistryManager(queryClient);
  }

  @Test
  void aggregatesFactoriesAndSortsByIssuerThenContractId() {
    when(queryClient.findCredentialRegistryFactories("admin"))
        .thenReturn(
            List.of(
                new CredentialRegistryFactory("contract-b", "admin", "issuer-b"),
                new CredentialRegistryFactory("contract-c", "admin", "issuer-a"),
                new CredentialRegistryFactory("contract-a", "admin", "issuer-a")));

    var registry = manager.findByRegistryId("admin");

    assertEquals("admin", registry.registryId());
    assertEquals("contract-a", registry.issuanceFactories().get(0).contractId());
    assertEquals("contract-c", registry.issuanceFactories().get(1).contractId());
    assertEquals("contract-b", registry.issuanceFactories().get(2).contractId());
  }

  @Test
  void returnsNotFoundForUnknownRegistry() {
    when(queryClient.findCredentialRegistryFactories("missing")).thenReturn(List.of());
    assertThrows(
        CredentialRegistryNotFoundException.class,
        () -> manager.findByRegistryId("missing"));
  }

  @Test
  void pagesDistinctRegistryIdsBeforeAggregation() {
    when(queryClient.findCredentialRegistryIds(0, 51)).thenReturn(List.of("admin"));
    when(queryClient.findCredentialRegistryFactories("admin"))
        .thenReturn(List.of(new CredentialRegistryFactory("contract", "admin", "issuer")));

    var page = manager.findPage(null, null);

    assertEquals(50, page.pageSize());
    assertEquals(1, page.items().size());
    assertEquals(false, page.hasNext());
    verify(queryClient).findCredentialRegistryIds(0, 51);
  }

  @Test
  void validatesPagination() {
    assertThrows(InvalidPaginationException.class, () -> manager.findPage("-1", "50"));
    assertThrows(InvalidPaginationException.class, () -> manager.findPage("0", "101"));
    assertThrows(InvalidPaginationException.class, () -> manager.findPage("bogus", "50"));
  }
}
