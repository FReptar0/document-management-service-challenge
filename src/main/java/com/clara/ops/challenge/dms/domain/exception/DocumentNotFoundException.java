package com.clara.ops.challenge.dms.domain.exception;

import java.util.UUID;

/** Thrown when a {@code Document} lookup by id finds no row. Maps to HTTP 404 (ADR-0006). */
public final class DocumentNotFoundException extends RuntimeException {

  private final UUID documentId;

  public DocumentNotFoundException(UUID documentId) {
    super("No document with id " + documentId);
    this.documentId = documentId;
  }

  public UUID documentId() {
    return documentId;
  }
}
