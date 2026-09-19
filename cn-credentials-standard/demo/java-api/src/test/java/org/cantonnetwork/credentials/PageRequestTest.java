package org.cantonnetwork.credentials;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class PageRequestTest {
  @Test
  void appliesDefaults() {
    assertEquals(new PageRequest(0, 50, 0), PageRequest.parse(null, null));
  }

  @Test
  void computesOffset() {
    assertEquals(new PageRequest(7, 100, 700), PageRequest.parse("7", "100"));
  }

  @Test
  void rejectsInvalidBoundsAndNumbers() {
    assertThrows(IllegalArgumentException.class, () -> PageRequest.parse("-1", "50"));
    assertThrows(IllegalArgumentException.class, () -> PageRequest.parse("0", "0"));
    assertThrows(IllegalArgumentException.class, () -> PageRequest.parse("0", "101"));
    assertThrows(IllegalArgumentException.class, () -> PageRequest.parse("not-a-number", "50"));
  }
}
