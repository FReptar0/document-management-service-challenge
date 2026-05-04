package com.clara.ops.challenge.dms.application;

import java.util.Set;

/**
 * Already-validated search inputs handed by the controller to the search use case. Each field is
 * optional — {@code null} means "do not filter by this attribute". {@code tagNames} is empty
 * (rather than {@code null}) when the caller passed an empty list, so the adapter can treat both
 * uniformly.
 */
public record SearchCriteria(String userId, String namePattern, Set<String> tagNames) {

  public SearchCriteria {
    tagNames = tagNames == null ? Set.of() : Set.copyOf(tagNames);
  }
}
