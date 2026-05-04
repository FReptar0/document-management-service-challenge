package com.clara.ops.challenge.dms.infrastructure.persistence;

import com.clara.ops.challenge.dms.application.PageResult;
import com.clara.ops.challenge.dms.application.SearchCriteria;
import com.clara.ops.challenge.dms.domain.Document;
import com.clara.ops.challenge.dms.domain.Tag;
import com.clara.ops.challenge.dms.domain.port.DocumentRepository;
import com.clara.ops.challenge.dms.infrastructure.persistence.entity.DocumentEntity;
import com.clara.ops.challenge.dms.infrastructure.persistence.entity.TagEntity;
import com.clara.ops.challenge.dms.infrastructure.persistence.jpa.DocumentJpaRepository;
import com.clara.ops.challenge.dms.infrastructure.persistence.jpa.TagJpaRepository;
import com.clara.ops.challenge.dms.infrastructure.persistence.mapper.DocumentEntityMapper;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * JPA-backed implementation of the {@link DocumentRepository} port. Tag insertion is idempotent via
 * {@link #upsertTag} so concurrent uploads sharing tag names do not race on the UNIQUE constraint.
 */
@Repository
public class DocumentRepositoryAdapter implements DocumentRepository {

  private final DocumentJpaRepository documentJpa;
  private final TagJpaRepository tagJpa;

  public DocumentRepositoryAdapter(DocumentJpaRepository documentJpa, TagJpaRepository tagJpa) {
    this.documentJpa = documentJpa;
    this.tagJpa = tagJpa;
  }

  @Override
  @Transactional
  public Document save(Document document) {
    Set<TagEntity> tagEntities = new HashSet<>();
    for (Tag tag : document.tags()) {
      tagEntities.add(upsertTag(tag));
    }
    DocumentEntity entity = DocumentEntityMapper.toEntity(document, tagEntities);
    DocumentEntity saved = documentJpa.save(entity);
    return DocumentEntityMapper.toDomain(saved);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<Document> findById(UUID id) {
    return documentJpa.findById(id).map(DocumentEntityMapper::toDomain);
  }

  @Override
  @Transactional(readOnly = true)
  public PageResult<Document> search(SearchCriteria criteria, int page, int size) {
    Specification<DocumentEntity> spec =
        Specification.allOf(
            DocumentSpecifications.userIdEquals(criteria.userId()),
            DocumentSpecifications.nameContainsIgnoreCase(criteria.namePattern()),
            DocumentSpecifications.hasAnyTag(criteria.tagNames()));
    PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
    Page<DocumentEntity> result = documentJpa.findAll(spec, pageRequest);
    List<Document> docs = result.stream().map(DocumentEntityMapper::toDomain).toList();
    return new PageResult<>(docs, page, size, result.getTotalElements());
  }

  /**
   * Idempotent tag insertion. If two transactions race on the same new tag, the loser catches the
   * UNIQUE violation and re-reads the winner's row.
   */
  private TagEntity upsertTag(Tag tag) {
    return tagJpa
        .findByName(tag.name())
        .orElseGet(
            () -> {
              try {
                return tagJpa.save(new TagEntity(tag.name()));
              } catch (DataIntegrityViolationException e) {
                return tagJpa.findByName(tag.name()).orElseThrow(() -> e);
              }
            });
  }
}
