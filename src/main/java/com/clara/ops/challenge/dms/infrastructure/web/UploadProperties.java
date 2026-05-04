package com.clara.ops.challenge.dms.infrastructure.web;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Upload-time enforcement knobs (ADR-0011 contract, ADR-0009 rules).
 *
 * <p>{@code maxSizeBytes} caps the entire request body — anything beyond fails fast with 413 before
 * the storage write begins. {@code enforcePdfMagic} toggles the {@code %PDF-} sniff at the first 5
 * bytes of the {@code file} part (negative tests turn it off to assert on the Storage path
 * independently).
 */
@ConfigurationProperties(prefix = "app.upload")
public record UploadProperties(long maxSizeBytes, boolean enforcePdfMagic) {}
