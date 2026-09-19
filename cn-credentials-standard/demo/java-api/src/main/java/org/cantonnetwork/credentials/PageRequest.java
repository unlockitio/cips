package org.cantonnetwork.credentials;

public record PageRequest(int page, int pageSize, long offset) {
  public static PageRequest parse(String pageValue, String pageSizeValue) {
    int page = parseInt(pageValue, 0, "page");
    int pageSize = parseInt(pageSizeValue, 50, "pageSize");
    if (page < 0) throw new IllegalArgumentException("page must be at least 0");
    if (pageSize < 1 || pageSize > 100) {
      throw new IllegalArgumentException("pageSize must be between 1 and 100");
    }
    try {
      return new PageRequest(page, pageSize, Math.multiplyExact((long) page, pageSize));
    } catch (ArithmeticException exception) {
      throw new IllegalArgumentException("pagination offset overflows", exception);
    }
  }

  private static int parseInt(String value, int defaultValue, String name) {
    if (value == null) return defaultValue;
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException exception) {
      throw new IllegalArgumentException(name + " must be an integer", exception);
    }
  }
}
