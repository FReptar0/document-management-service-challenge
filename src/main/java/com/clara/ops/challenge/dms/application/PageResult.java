package com.clara.ops.challenge.dms.application;

import java.util.List;

/**
 * Framework-agnostic paginated result. The adapter converts Spring Data's {@code Page} into this
 * record so the application/domain layers stay free of {@code org.springframework.data} imports
 * (ADR-0001). {@code totalPages} is derived to avoid storing redundant state.
 */
public record PageResult<T>(List<T> content, int pageNumber, int pageSize, long totalElements) {

  public PageResult {
    content = List.copyOf(content);
  }

  public int totalPages() {
    return pageSize == 0 ? 0 : (int) Math.ceil((double) totalElements / pageSize);
  }
}
