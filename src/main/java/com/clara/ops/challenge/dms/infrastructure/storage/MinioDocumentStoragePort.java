package com.clara.ops.challenge.dms.infrastructure.storage;

import com.clara.ops.challenge.dms.domain.port.DocumentStoragePort;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.errors.ErrorResponseException;
import io.minio.http.Method;
import java.io.InputStream;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * MinIO-backed implementation of {@link DocumentStoragePort}. Streaming put uses the internal
 * client and a 5 MB part buffer (S3 multipart minimum) so heap stays bounded regardless of file
 * size — see ADR-0002. Presigned GETs use the public client so signatures match the host the HTTP
 * client will actually contact — see ADR-0004.
 */
@Component
public class MinioDocumentStoragePort implements DocumentStoragePort {

  private static final Logger log = LoggerFactory.getLogger(MinioDocumentStoragePort.class);
  private static final long PART_SIZE_BYTES = 5L * 1024 * 1024; // 5 MB — S3 multipart minimum.
  private static final long UNKNOWN_TOTAL_SIZE = -1L;

  private final MinioClient internalClient;
  private final MinioClient publicClient;
  private final MinioProperties props;

  public MinioDocumentStoragePort(
      MinioClient internalClient,
      @Qualifier("minioPublic") MinioClient publicClient,
      MinioProperties props) {
    this.internalClient = internalClient;
    this.publicClient = publicClient;
    this.props = props;
  }

  @Override
  public void put(InputStream content, String objectKey, String contentType) {
    try {
      internalClient.putObject(
          PutObjectArgs.builder()
              .bucket(props.bucket())
              .object(objectKey)
              .stream(content, UNKNOWN_TOTAL_SIZE, PART_SIZE_BYTES)
              .contentType(contentType)
              .build());
    } catch (Exception e) {
      throw new MinioStorageException("Failed to put object " + objectKey, e);
    }
  }

  @Override
  public String presignedGet(String objectKey, Duration ttl) {
    try {
      return publicClient.getPresignedObjectUrl(
          GetPresignedObjectUrlArgs.builder()
              .bucket(props.bucket())
              .object(objectKey)
              .method(Method.GET)
              .expiry((int) ttl.toSeconds(), TimeUnit.SECONDS)
              .build());
    } catch (Exception e) {
      throw new MinioStorageException("Failed to presign GET for " + objectKey, e);
    }
  }

  @Override
  public void delete(String objectKey) {
    try {
      internalClient.removeObject(
          RemoveObjectArgs.builder().bucket(props.bucket()).object(objectKey).build());
    } catch (ErrorResponseException e) {
      // NoSuchKey is acceptable for a best-effort delete.
      log.warn("Best-effort delete swallowed MinIO error for {}: {}", objectKey, e.getMessage());
    } catch (Exception e) {
      log.warn("Best-effort delete failed for {}: {}", objectKey, e.getMessage());
    }
  }
}
