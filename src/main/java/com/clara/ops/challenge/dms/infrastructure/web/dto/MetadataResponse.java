package com.clara.ops.challenge.dms.infrastructure.web.dto;

/**
 * Pagination metadata mirroring the {@code Metadata} schema in the OpenAPI spec. {@code
 * currentItems} is the size of the page actually returned (may be smaller than {@code itemsPerPage}
 * for the final page).
 */
public record MetadataResponse(
    int currentPage, int itemsPerPage, int currentItems, int totalPages, long totalItems) {}
