package com.clara.ops.challenge.dms.infrastructure.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Set;

/**
 * The {@code metadata} JSON part of the multipart upload contract (ADR-0011). Field names mirror
 * the {@code UploadDocument} schema in {@code docs/document-management-open-api.yml}: the public
 * field is {@code user}, internally we name it {@code userId} for clarity.
 */
public record UploadMetadataRequest(
    @JsonProperty("user")
        @NotBlank
        @Size(max = 128)
        @Pattern(regexp = "[^/\\\\]+", message = "user must not contain path separators")
        String userId,
    @NotBlank
        @Size(max = 255)
        @Pattern(regexp = "[^/\\\\]+", message = "name must not contain path separators")
        String name,
    @Size(max = 32) Set<@NotBlank @Size(max = 64) String> tags) {}
