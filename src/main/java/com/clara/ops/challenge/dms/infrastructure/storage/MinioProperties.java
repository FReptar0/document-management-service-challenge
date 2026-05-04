package com.clara.ops.challenge.dms.infrastructure.storage;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Bound to {@code app.minio.*}. Two endpoints are kept distinct so presigned URLs (signed against
 * {@link #publicEndpoint}) remain reachable from the HTTP client even when the service talks to
 * MinIO over the compose network ({@link #endpoint} = {@code http://minio:9000}). See ADR-0004.
 */
@Validated
@ConfigurationProperties(prefix = "app.minio")
public record MinioProperties(
    @NotBlank String endpoint,
    @NotBlank String publicEndpoint,
    @NotBlank String accessKey,
    @NotBlank String secretKey,
    @NotBlank String bucket,
    @NotNull @Valid Presigned presigned) {

  public record Presigned(@Min(1) long ttlSeconds) {}
}
