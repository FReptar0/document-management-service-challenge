package com.clara.ops.challenge.dms.infrastructure.storage;

/**
 * Wraps low-level MinIO SDK exceptions into a single unchecked type. The web error advice (ADR-0006
 * / Phase 8) maps this to HTTP 502 (storage backend unavailable).
 */
public class MinioStorageException extends RuntimeException {

  public MinioStorageException(String message, Throwable cause) {
    super(message, cause);
  }
}
