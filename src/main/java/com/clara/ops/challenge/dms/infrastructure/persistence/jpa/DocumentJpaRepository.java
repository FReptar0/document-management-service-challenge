package com.clara.ops.challenge.dms.infrastructure.persistence.jpa;

import com.clara.ops.challenge.dms.infrastructure.persistence.entity.DocumentEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * Spring Data interface for {@link DocumentEntity}. Wrapped by {@code DocumentRepositoryAdapter}.
 *
 * <p>{@link JpaSpecificationExecutor} provides the dynamic-criteria fast path used by the search
 * endpoint (Phase 6).
 */
public interface DocumentJpaRepository
    extends JpaRepository<DocumentEntity, UUID>, JpaSpecificationExecutor<DocumentEntity> {}
