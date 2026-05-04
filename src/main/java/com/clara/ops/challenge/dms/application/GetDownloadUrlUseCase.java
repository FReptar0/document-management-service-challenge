package com.clara.ops.challenge.dms.application;

import com.clara.ops.challenge.dms.domain.Document;
import com.clara.ops.challenge.dms.domain.exception.DocumentNotFoundException;
import com.clara.ops.challenge.dms.domain.port.DocumentRepository;
import com.clara.ops.challenge.dms.domain.port.DocumentStoragePort;
import java.time.Duration;
import java.util.UUID;

/**
 * Resolves a document id to a presigned GET URL. The DB row is the source of truth for the object
 * key — we never trust client-supplied keys — and the URL is signed against the public MinIO
 * endpoint (via the {@code minioPublic} client wired in {@code MinioConfiguration}).
 *
 * <p>No Spring annotations (ADR-0001).
 */
public class GetDownloadUrlUseCase {

  private final DocumentRepository repository;
  private final DocumentStoragePort storage;
  private final Duration ttl;

  public GetDownloadUrlUseCase(
      DocumentRepository repository, DocumentStoragePort storage, Duration ttl) {
    this.repository = repository;
    this.storage = storage;
    this.ttl = ttl;
  }

  public String execute(UUID documentId) {
    Document doc =
        repository
            .findById(documentId)
            .orElseThrow(() -> new DocumentNotFoundException(documentId));
    return storage.presignedGet(doc.objectKey(), ttl);
  }
}
