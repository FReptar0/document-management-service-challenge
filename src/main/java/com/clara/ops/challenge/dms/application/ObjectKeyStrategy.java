package com.clara.ops.challenge.dms.application;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Deterministic, collision-proof object key construction for the upload pipeline (ADR-0004). Pure
 * function of {@code (userId, documentId, name)} so it can be stable, idempotent, and trivially
 * unit-testable.
 *
 * <p>Format: {@code <userId>/<documentId>__<sanitized-name>}. The sanitized name is lower-cased,
 * collapsed whitespace, stripped of unsafe characters, capped at 200 chars, and forced to end in
 * {@code .pdf}.
 */
public final class ObjectKeyStrategy {

  private static final int MAX_SANITIZED_NAME_LENGTH = 200;
  private static final Pattern WHITESPACE = Pattern.compile("\\s+");
  private static final Pattern UNSAFE_CHARS = Pattern.compile("[^a-z0-9._-]");
  private static final String PDF_SUFFIX = ".pdf";
  private static final String FALLBACK_BASE_NAME = "document";

  private ObjectKeyStrategy() {
    // utility
  }

  public static String forUpload(String userId, UUID documentId, String name) {
    return userId + "/" + documentId + "__" + sanitize(name);
  }

  static String sanitize(String name) {
    String lowered = name.trim().toLowerCase();
    String dashed = WHITESPACE.matcher(lowered).replaceAll("-");
    String stripped = UNSAFE_CHARS.matcher(dashed).replaceAll("");
    String trimmed =
        stripped.length() > MAX_SANITIZED_NAME_LENGTH
            ? stripped.substring(0, MAX_SANITIZED_NAME_LENGTH)
            : stripped;
    String base = trimmed.isEmpty() ? FALLBACK_BASE_NAME : trimmed;
    return base.endsWith(PDF_SUFFIX) ? base : base + PDF_SUFFIX;
  }
}
