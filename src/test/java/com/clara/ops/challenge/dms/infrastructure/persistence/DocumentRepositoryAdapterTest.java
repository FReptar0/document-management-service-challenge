package com.clara.ops.challenge.dms.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.clara.ops.challenge.dms.domain.Document;
import com.clara.ops.challenge.dms.domain.Tag;
import com.clara.ops.challenge.dms.infrastructure.persistence.jpa.TagJpaRepository;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

/**
 * Slice test for {@link DocumentRepositoryAdapter}. Uses the Testcontainers JDBC URL configured in
 * application-test.yml; spring.sql.init applies docker/init-scripts/schema-init.sql (copied onto
 * the test classpath via Maven testResources).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Import(DocumentRepositoryAdapter.class)
class DocumentRepositoryAdapterTest {

  @Autowired private DocumentRepositoryAdapter adapter;
  @Autowired private TagJpaRepository tagJpa;

  @Test
  void save_then_findById_round_trip_preserves_metadata_and_tags() {
    Document doc =
        Document.create(
            "alice",
            "Q3 Finance Report.pdf",
            "alice/" + UUID.randomUUID() + "__q3-finance-report.pdf",
            12_345L,
            "application/pdf",
            Set.of(Tag.normalize("Finance"), Tag.normalize("Q3")));

    Document saved = adapter.save(doc);
    Optional<Document> found = adapter.findById(saved.id());

    assertThat(found).isPresent();
    Document hydrated = found.get();
    assertThat(hydrated.id()).isEqualTo(doc.id());
    assertThat(hydrated.userId()).isEqualTo("alice");
    assertThat(hydrated.name()).isEqualTo("Q3 Finance Report.pdf");
    assertThat(hydrated.objectKey()).isEqualTo(doc.objectKey());
    assertThat(hydrated.fileSize()).isEqualTo(12_345L);
    assertThat(hydrated.fileType()).isEqualTo("application/pdf");
    assertThat(hydrated.tags()).extracting(Tag::name).containsExactlyInAnyOrder("finance", "q3");
    assertThat(hydrated.createdAt()).isNotNull();
  }

  @Test
  void tags_are_deduped_across_documents_sharing_a_name() {
    adapter.save(
        Document.create(
            "alice",
            "a.pdf",
            "alice/" + UUID.randomUUID() + "__a.pdf",
            1L,
            "application/pdf",
            Set.of(Tag.normalize("Finance"))));
    adapter.save(
        Document.create(
            "bob",
            "b.pdf",
            "bob/" + UUID.randomUUID() + "__b.pdf",
            2L,
            "application/pdf",
            Set.of(Tag.normalize("FINANCE"))));

    long financeRows =
        tagJpa.findAll().stream().filter(t -> "finance".equals(t.getName())).count();

    assertThat(financeRows).as("'finance' must exist exactly once").isOne();
  }

  @Test
  void findById_returns_empty_for_unknown_id() {
    assertThat(adapter.findById(UUID.randomUUID())).isEmpty();
  }
}
