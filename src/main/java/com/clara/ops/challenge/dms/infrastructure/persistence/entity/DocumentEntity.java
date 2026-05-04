package com.clara.ops.challenge.dms.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * JPA mapping for {@code document_schema.documents}. The {@link #tags} association is owned here
 * (the join table is declared with {@link JoinTable}); fetch is LAZY so list endpoints do not
 * trigger N+1 reads (search endpoint will add an explicit fetch join in Phase 6).
 */
@Entity
@Table(name = "documents")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DocumentEntity {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "user_id", nullable = false, length = 128)
  private String userId;

  @Column(name = "name", nullable = false, length = 512)
  private String name;

  @Column(name = "object_key", nullable = false, length = 1024, unique = true)
  private String objectKey;

  @Column(name = "file_size", nullable = false)
  private long fileSize;

  @Column(name = "file_type", nullable = false, length = 127)
  private String fileType;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(
      name = "document_tags",
      joinColumns = @JoinColumn(name = "document_id"),
      inverseJoinColumns = @JoinColumn(name = "tag_id"))
  private Set<TagEntity> tags = new HashSet<>();

  public DocumentEntity(
      UUID id,
      String userId,
      String name,
      String objectKey,
      long fileSize,
      String fileType,
      Instant createdAt,
      Set<TagEntity> tags) {
    this.id = id;
    this.userId = userId;
    this.name = name;
    this.objectKey = objectKey;
    this.fileSize = fileSize;
    this.fileType = fileType;
    this.createdAt = createdAt;
    this.tags = tags;
  }
}
