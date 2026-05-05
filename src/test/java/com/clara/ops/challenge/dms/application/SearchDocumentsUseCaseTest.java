package com.clara.ops.challenge.dms.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.clara.ops.challenge.dms.domain.Document;
import com.clara.ops.challenge.dms.domain.Tag;
import com.clara.ops.challenge.dms.domain.port.DocumentRepository;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * The use case is intentionally a thin pass-through so we keep the surface area small (ADR-0007 §
 * "Unit tests"). The point of this test is regression: if a future change adds caching, audit, or
 * input mutation, we want a failing assertion before we hit production traffic.
 */
@ExtendWith(MockitoExtension.class)
class SearchDocumentsUseCaseTest {

  @Mock private DocumentRepository repository;

  @Test
  void delegates_criteria_and_pagination_to_repository_unchanged() {
    SearchCriteria criteria = new SearchCriteria("alice", "report", Set.of("finance"));
    PageResult<Document> expected =
        new PageResult<>(List.of(sampleDoc("alice", "report-q1.pdf")), 0, 20, 1L);
    when(repository.search(criteria, 0, 20)).thenReturn(expected);

    PageResult<Document> result = new SearchDocumentsUseCase(repository).execute(criteria, 0, 20);

    assertThat(result).isSameAs(expected);
    verify(repository).search(eq(criteria), eq(0), eq(20));
    verifyNoMoreInteractions(repository);
  }

  @Test
  void empty_criteria_still_propagates_pagination() {
    SearchCriteria criteria = new SearchCriteria(null, null, null);
    PageResult<Document> empty = new PageResult<>(List.of(), 3, 5, 0L);
    when(repository.search(criteria, 3, 5)).thenReturn(empty);

    PageResult<Document> result = new SearchDocumentsUseCase(repository).execute(criteria, 3, 5);

    assertThat(result.content()).isEmpty();
    assertThat(result.pageNumber()).isEqualTo(3);
    assertThat(result.pageSize()).isEqualTo(5);
    assertThat(result.totalPages()).isZero();
  }

  private static Document sampleDoc(String userId, String name) {
    return Document.rehydrate(
        UUID.randomUUID(),
        userId,
        name,
        userId + "/" + UUID.randomUUID() + "__" + name,
        1024L,
        "application/pdf",
        Set.of(Tag.normalize("finance")),
        Instant.now());
  }
}
