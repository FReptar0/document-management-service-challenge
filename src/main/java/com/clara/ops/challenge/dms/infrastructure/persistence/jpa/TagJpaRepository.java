package com.clara.ops.challenge.dms.infrastructure.persistence.jpa;

import com.clara.ops.challenge.dms.infrastructure.persistence.entity.TagEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data interface for {@link TagEntity}. */
public interface TagJpaRepository extends JpaRepository<TagEntity, Long> {

  /** Locate an existing tag by its normalized name. Used by the upsert path. */
  Optional<TagEntity> findByName(String name);
}
