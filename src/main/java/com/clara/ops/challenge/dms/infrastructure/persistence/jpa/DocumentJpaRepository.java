package com.clara.ops.challenge.dms.infrastructure.persistence.jpa;

import com.clara.ops.challenge.dms.infrastructure.persistence.entity.DocumentEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data interface for {@link DocumentEntity}. Wrapped by {@code DocumentRepositoryAdapter}.
 */
public interface DocumentJpaRepository extends JpaRepository<DocumentEntity, UUID> {}
