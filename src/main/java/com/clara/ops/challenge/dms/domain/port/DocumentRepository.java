package com.clara.ops.challenge.dms.domain.port;

import com.clara.ops.challenge.dms.domain.Document;
import java.util.Optional;
import java.util.UUID;

/**
 * Driven port for persisting and reading {@link Document} aggregates. Adapter lives in
 * {@code infrastructure/persistence}.
 *
 * <p>Search/pagination operations are added in Phase 6; this Phase-3 port only exposes the upload
 * (save) and download (findById) paths.
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
}
