package com.clara.ops.challenge.dms.infrastructure.web.dto;

import com.clara.ops.challenge.dms.domain.Document;
import com.clara.ops.challenge.dms.domain.Tag;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Wire response for a {@link Document}. Mirrors the {@code Document} schema in {@code
 * docs/document-management-open-api.yml} — note {@code user} (not userId) and {@code size} (not
 * fileSize) for OpenAPI compatibility.
 */
public record DocumentResponse(
    UUID id,
    @JsonProperty("user") String userId,
    String name,
    Set<String> tags,
    @JsonProperty("size") long fileSize,
    @JsonProperty("type") String fileType,
    Instant createdAt) {

  public static DocumentResponse from(Document doc) {
    return new DocumentResponse(
        doc.id(),
        doc.userId(),
        doc.name(),
        doc.tags().stream().map(Tag::name).collect(Collectors.toUnmodifiableSet()),
        doc.fileSize(),
        doc.fileType(),
        doc.createdAt());
  }
}
