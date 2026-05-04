package com.clara.ops.challenge.dms.infrastructure.persistence.jpa;

import com.clara.ops.challenge.dms.infrastructure.persistence.entity.TagEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data interface for {@link TagEntity}. */
public interface TagJpaRepository extends JpaRepository<TagEntity, Long> {

  /** Locate an existing tag by its normalized name. Used by the upsert path. */
  Optional<TagEntity> findByName(String name);

  /**
   * Race-safe insert: noop when the row already exists. Avoids the {@code
   * DataIntegrityViolationException} path used previously, which corrupted the Hibernate session
   * under concurrent uploads sharing a tag (HHH000099).
   */
  @Modifying
  @Query(
      value =
          "INSERT INTO document_schema.tags (name) VALUES (:name) ON CONFLICT (name) DO NOTHING",
      nativeQuery = true)
  void insertIfAbsent(@Param("name") String name);
}
