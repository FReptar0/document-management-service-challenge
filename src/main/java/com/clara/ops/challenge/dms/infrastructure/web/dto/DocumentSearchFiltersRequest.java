package com.clara.ops.challenge.dms.infrastructure.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Size;
import java.util.Set;

/**
 * Body of {@code POST /document-management/search}. Mirrors the {@code DocumentSearchFilters}
 * schema in {@code docs/document-management-open-api.yml}: every field is nullable so each filter
 * is opt-in.
 */
public record DocumentSearchFiltersRequest(
    @JsonProperty("user") @Size(max = 128) String userId,
    @Size(max = 255) String name,
    @Size(max = 32) Set<@Size(max = 64) String> tags) {}
