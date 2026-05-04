package com.clara.ops.challenge.dms.domain.port;

import java.io.InputStream;
import java.time.Duration;

/**
 * Driven port for the binary-content store. The adapter (MinIO in this project) handles streaming
 * I/O without buffering full payloads in memory — see ADR-0002.
 *
 * <p>Object keys are opaque from the domain's perspective; key shape is decided by the use case
 * layer (ADR-0004 specifies {@code {userId}/{documentId}__{name}.pdf}).
 */
public interface DocumentStoragePort {

  /**
   * Stream {@code content} into the configured bucket under {@code objectKey}. The implementation
   * reads from {@code content} with a bounded part buffer (5 MB) and never copies the full payload
   * into a single in-memory buffer.
   *
   * @param content the stream to upload; the caller owns its lifecycle (do not close from inside
   *     the adapter beyond what the SDK already does on EOF).
   * @param objectKey the destination key relative to the bucket root.
   * @param contentType MIME type recorded on the object metadata (e.g. {@code application/pdf}).
   */
  void put(InputStream content, String objectKey, String contentType);

  /**
   * Generate a short-lived URL that allows a {@code GET} on {@code objectKey} without service
   * involvement. Default TTL is configured via {@code app.minio.presigned.ttl-seconds}; callers
   * may pass an explicit override.
   */
  String presignedGet(String objectKey, Duration ttl);

  /**
   * Best-effort delete used by the upload use case to roll back a stored object when the
   * subsequent DB write fails. Silent on missing keys.
   */
  void delete(String objectKey);
}
