package com.clara.ops.challenge.dms.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Immutable representation of a stored PDF document and its metadata.
 *
 * <p>Identity is the {@code id} (UUID, app-generated). Two factories: {@link #create} for the
 * upload path (assigns a fresh UUID and {@code now()}), {@link #rehydrate} for reads from
 * persistence (uses the stored id and timestamp).
 */
public final class Document {

  private final UUID id;
  private final String userId;
  private final String name;
  private final String objectKey;
  private final long fileSize;
  private final String fileType;
  private final Set<Tag> tags;
  private final Instant createdAt;

  private Document(
      UUID id,
      String userId,
      String name,
      String objectKey,
      long fileSize,
      String fileType,
      Set<Tag> tags,
      Instant createdAt) {
    this.id = Objects.requireNonNull(id, "id");
    this.userId = requireNonBlank(userId, "userId");
    this.name = requireNonBlank(name, "name");
    this.objectKey = requireNonBlank(objectKey, "objectKey");
    if (fileSize < 0) {
      throw new IllegalArgumentException("fileSize must be >= 0, got " + fileSize);
    }
    this.fileSize = fileSize;
    this.fileType = requireNonBlank(fileType, "fileType");
    this.tags = Set.copyOf(Objects.requireNonNull(tags, "tags"));
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
  }

  /** Build a new {@code Document} for upload — assigns a fresh UUID and {@code Instant.now()}. */
  public static Document create(
      String userId,
      String name,
      String objectKey,
      long fileSize,
      String fileType,
      Set<Tag> tags) {
    return new Document(
        UUID.randomUUID(), userId, name, objectKey, fileSize, fileType, tags, Instant.now());
  }

  /** Reconstruct a {@code Document} from persistence. Validation still applies. */
  public static Document rehydrate(
      UUID id,
      String userId,
      String name,
      String objectKey,
      long fileSize,
      String fileType,
      Set<Tag> tags,
      Instant createdAt) {
    return new Document(id, userId, name, objectKey, fileSize, fileType, tags, createdAt);
  }

  public UUID id() {
    return id;
  }

  public String userId() {
    return userId;
  }

  public String name() {
    return name;
  }

  public String objectKey() {
    return objectKey;
  }

  public long fileSize() {
    return fileSize;
  }

  public String fileType() {
    return fileType;
  }

  public Set<Tag> tags() {
    return tags;
  }

  public Instant createdAt() {
    return createdAt;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof Document other)) return false;
    return id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return id.hashCode();
  }

  @Override
  public String toString() {
    return "Document(id="
        + id
        + ", user="
        + userId
        + ", name="
        + name
        + ", objectKey="
        + objectKey
        + ", size="
        + fileSize
        + ")";
  }

  private static String requireNonBlank(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return value;
  }
}
