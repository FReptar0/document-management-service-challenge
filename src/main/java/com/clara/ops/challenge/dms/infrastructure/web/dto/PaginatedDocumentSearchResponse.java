package com.clara.ops.challenge.dms.infrastructure.web.dto;

import com.clara.ops.challenge.dms.application.PageResult;
import com.clara.ops.challenge.dms.domain.Document;
import java.util.List;

/**
 * Search-endpoint envelope. Mirrors the {@code PaginatedDocumentSearch} schema in the OpenAPI spec.
 * Build via {@link #from(PageResult)} so the controller stays free of mapping logic.
 */
public record PaginatedDocumentSearchResponse(
    MetadataResponse metadata, List<DocumentResponse> documents) {

  public static PaginatedDocumentSearchResponse from(PageResult<Document> page) {
    List<DocumentResponse> documents = page.content().stream().map(DocumentResponse::from).toList();
    MetadataResponse meta =
        new MetadataResponse(
            page.pageNumber(),
            page.pageSize(),
            documents.size(),
            page.totalPages(),
            page.totalElements());
    return new PaginatedDocumentSearchResponse(meta, documents);
  }
}
