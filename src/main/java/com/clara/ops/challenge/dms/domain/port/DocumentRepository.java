package com.clara.ops.challenge.dms.domain.port;

import com.clara.ops.challenge.dms.application.PageResult;
import com.clara.ops.challenge.dms.application.SearchCriteria;
import com.clara.ops.challenge.dms.domain.Document;
import java.util.Optional;
import java.util.UUID;

/**
 * Driven port for persisting and reading {@link Document} aggregates. Adapter lives in {@code
 * infrastructure/persistence}.
 */
public interface DocumentRepository {

  /**
   * Persist a new {@code Document} along with its tags. Tags not yet present in the dictionary are
   * created on the fly; existing tags are reused. Returns the same domain object (canonical id and
   * created_at preserved).
   */
  Document save(Document document);

  /** Look up a document by id. Empty if no row matches. */
  Optional<Document> findById(UUID id);

  /**
   * Composable search. Any null filter is ignored. {@code namePattern} is matched substring-wise
   * (PostgreSQL pg_trgm fast-path). {@code tagNames} matches a document if it carries <em>any</em>
   * of the listed tags. Default sort is {@code created_at DESC}; the adapter is responsible for
   * mapping {@code page} and {@code size} onto the underlying engine.
   */
  PageResult<Document> search(SearchCriteria criteria, int page, int size);
}
