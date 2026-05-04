package com.clara.ops.challenge.dms.domain;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Normalized document tag. Identity is the lowercase name; instances are interned by value.
 *
 * <p>Normalization rules (ADR-0009): trim leading/trailing whitespace, lowercase, allowed charset
 * {@code [a-z0-9_-]}, length {@code 1..64}. Construction via {@link #normalize(String)} is the only
 * supported path so the invariants always hold.
 */
public final class Tag {

  public static final int MAX_LENGTH = 64;
  private static final Pattern ALLOWED = Pattern.compile("^[a-z0-9_-]+$");

  private final String name;

  private Tag(String name) {
    this.name = name;
  }

  /**
   * Normalize a raw tag value and return a {@code Tag} or throw {@link IllegalArgumentException} if
   * the result violates the rules.
   */
  public static Tag normalize(String raw) {
    Objects.requireNonNull(raw, "tag must not be null");
    String trimmed = raw.trim().toLowerCase();
    if (trimmed.isEmpty()) {
      throw new IllegalArgumentException("tag must not be blank");
    }
    if (trimmed.length() > MAX_LENGTH) {
      throw new IllegalArgumentException(
          "tag exceeds " + MAX_LENGTH + " chars: " + trimmed.length());
    }
    if (!ALLOWED.matcher(trimmed).matches()) {
      throw new IllegalArgumentException(
          "tag contains characters outside [a-z0-9_-]: '" + raw + "'");
    }
    return new Tag(trimmed);
  }

  public String name() {
    return name;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof Tag other)) return false;
    return name.equals(other.name);
  }

  @Override
  public int hashCode() {
    return name.hashCode();
  }

  @Override
  public String toString() {
    return "Tag(" + name + ")";
  }
}
