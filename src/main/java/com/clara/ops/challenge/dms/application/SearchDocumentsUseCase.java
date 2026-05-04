package com.clara.ops.challenge.dms.application;

import com.clara.ops.challenge.dms.domain.Document;
import com.clara.ops.challenge.dms.domain.port.DocumentRepository;

/**
 * Search use case. The application layer keeps the call thin — the heavy lifting (criteria
 * composition, pagination, sort) is the adapter's responsibility — so this class exists mainly to
 * keep callers off the port directly and to leave room for cross-cutting concerns (audit, request
 * tagging) without modifying the port itself.
 *
 * <p>No Spring annotations (ADR-0001); wired in {@code infrastructure/config/UseCaseConfiguration}.
 */
public class SearchDocumentsUseCase {

  private final DocumentRepository repository;

  public SearchDocumentsUseCase(DocumentRepository repository) {
    this.repository = repository;
  }

  public PageResult<Document> execute(SearchCriteria criteria, int page, int size) {
    return repository.search(criteria, page, size);
  }
}
