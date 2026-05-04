package com.clara.ops.challenge.dms.infrastructure.web.dto;

/**
 * Response body for {@code GET /document-management/download/{id}}. Mirrors the {@code
 * DocumentDownloadUrl} schema in the OpenAPI spec.
 */
public record DocumentDownloadUrlResponse(String url) {}
