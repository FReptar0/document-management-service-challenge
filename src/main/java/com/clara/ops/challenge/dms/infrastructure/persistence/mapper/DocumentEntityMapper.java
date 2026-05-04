package com.clara.ops.challenge.dms.infrastructure.persistence.mapper;

import com.clara.ops.challenge.dms.domain.Document;
import com.clara.ops.challenge.dms.domain.Tag;
import com.clara.ops.challenge.dms.infrastructure.persistence.entity.DocumentEntity;
import com.clara.ops.challenge.dms.infrastructure.persistence.entity.TagEntity;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Stateless mapping between the {@link Document} domain aggregate and the {@link DocumentEntity}
 * JPA mapping. Static methods to keep the mapper trivially testable and free of Spring wiring.
 */
public final class DocumentEntityMapper {

  private DocumentEntityMapper() {
    // utility
  }

  /**
   * Materialize a {@code Document} from a managed {@code DocumentEntity}. Caller must invoke within
   * an active transaction so the LAZY tags collection can be initialised.
   */
  public static Document toDomain(DocumentEntity entity) {
    Set<Tag> tags =
        entity.getTags().stream()
            .map(t -> Tag.normalize(t.getName()))
            .collect(Collectors.toUnmodifiableSet());
    return Document.rehydrate(
        entity.getId(),
        entity.getUserId(),
        entity.getName(),
        entity.getObjectKey(),
        entity.getFileSize(),
        entity.getFileType(),
        tags,
        entity.getCreatedAt());
  }

  /**
   * Build a fresh {@code DocumentEntity} from a {@code Document} and a set of already-resolved tag
   * entities. The adapter is responsible for upserting the tag dictionary before calling this.
   */
  public static DocumentEntity toEntity(Document document, Set<TagEntity> tagEntities) {
    return new DocumentEntity(
        document.id(),
        document.userId(),
        document.name(),
        document.objectKey(),
        document.fileSize(),
        document.fileType(),
        document.createdAt(),
        tagEntities);
  }
}
