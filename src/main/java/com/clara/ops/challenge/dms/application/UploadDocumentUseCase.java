package com.clara.ops.challenge.dms.application;

import com.clara.ops.challenge.dms.domain.Document;
import com.clara.ops.challenge.dms.domain.Tag;
import com.clara.ops.challenge.dms.domain.port.DocumentRepository;
import com.clara.ops.challenge.dms.domain.port.DocumentStoragePort;
import java.io.InputStream;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Orchestrates the streaming PDF upload: stream to MinIO first, then persist metadata. If the DB
 * write fails after the object is in storage, a best-effort {@link DocumentStoragePort#delete}
 * compensates so we never leave orphaned objects behind.
 *
 * <p>No Spring annotations — wiring lives in {@code infrastructure/config/UseCaseConfiguration}
 * (ADR-0001 keeps the application layer free of framework leakage).
 */
public class UploadDocumentUseCase {

  private static final Logger log = LoggerFactory.getLogger(UploadDocumentUseCase.class);

  private final DocumentStoragePort storage;
  private final DocumentRepository repository;

  public UploadDocumentUseCase(DocumentStoragePort storage, DocumentRepository repository) {
    this.storage = storage;
    this.repository = repository;
  }

  public Document execute(UploadCommand cmd, InputStream content, String contentType) {
    UUID documentId = UUID.randomUUID();
    String objectKey = ObjectKeyStrategy.forUpload(cmd.userId(), documentId, cmd.name());

    CountingInputStream counter = new CountingInputStream(content);
    storage.put(counter, objectKey, contentType);
    long fileSize = counter.count();

    try {
      Set<Tag> tags =
          cmd.tags().stream().map(Tag::normalize).collect(Collectors.toUnmodifiableSet());
      Document doc =
          Document.rehydrate(
              documentId,
              cmd.userId(),
              cmd.name(),
              objectKey,
              fileSize,
              contentType,
              tags,
              Instant.now());
      Document saved = repository.save(doc);
      log.info(
          "Uploaded document id={} user={} key={} bytes={}",
          saved.id(),
          saved.userId(),
          saved.objectKey(),
          fileSize);
      return saved;
    } catch (RuntimeException e) {
      log.warn(
          "Persistence failed for object {} after storage write — rolling back via delete",
          objectKey,
          e);
      storage.delete(objectKey);
      throw e;
    }
  }

  /** Already-validated input handed by the controller. */
  public record UploadCommand(String userId, String name, Set<String> tags) {}
}
