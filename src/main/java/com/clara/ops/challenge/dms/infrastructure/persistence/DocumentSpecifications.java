package com.clara.ops.challenge.dms.infrastructure.persistence;

import com.clara.ops.challenge.dms.infrastructure.persistence.entity.DocumentEntity;
import com.clara.ops.challenge.dms.infrastructure.persistence.entity.TagEntity;
import jakarta.persistence.criteria.JoinType;
import java.util.Set;
import org.springframework.data.jpa.domain.Specification;

/**
 * JPA Criteria builders for the search endpoint. Each builder returns a {@link Specification} that
 * is a no-op when its input is null/empty so callers can compose them with {@code
 * Specification.allOf} without conditional branches.
 */
public final class DocumentSpecifications {

  private DocumentSpecifications() {}

  /** Exact-match on {@code user_id}. No-op when {@code userId} is null/blank. */
  public static Specification<DocumentEntity> userIdEquals(String userId) {
    return (root, query, cb) -> {
      if (userId == null || userId.isBlank()) {
        return cb.conjunction();
      }
      return cb.equal(root.get("userId"), userId);
    };
  }

  /**
   * Case-insensitive substring match on {@code name}. The pg_trgm GIN index on this column makes
   * the LIKE pattern fast even with leading wildcards.
   */
  public static Specification<DocumentEntity> nameContainsIgnoreCase(String pattern) {
    return (root, query, cb) -> {
      if (pattern == null || pattern.isBlank()) {
        return cb.conjunction();
      }
      String like = "%" + pattern.toLowerCase() + "%";
      return cb.like(cb.lower(root.get("name")), like);
    };
  }

  /**
   * Document carries <em>any</em> of the listed tags (OR semantics). Uses {@code distinct} to avoid
   * row duplication when more than one tag matches a document.
   */
  public static Specification<DocumentEntity> hasAnyTag(Set<String> tagNames) {
    return (root, query, cb) -> {
      if (tagNames == null || tagNames.isEmpty()) {
        return cb.conjunction();
      }
      if (query != null) {
        query.distinct(true);
      }
      var tags = root.<DocumentEntity, TagEntity>join("tags", JoinType.INNER);
      return tags.get("name").in(tagNames);
    };
  }
}
