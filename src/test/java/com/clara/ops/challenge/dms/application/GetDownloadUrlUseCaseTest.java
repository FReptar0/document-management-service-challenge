package com.clara.ops.challenge.dms.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.clara.ops.challenge.dms.domain.Document;
import com.clara.ops.challenge.dms.domain.Tag;
import com.clara.ops.challenge.dms.domain.exception.DocumentNotFoundException;
import com.clara.ops.challenge.dms.domain.port.DocumentRepository;
import com.clara.ops.challenge.dms.domain.port.DocumentStoragePort;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Covers the two branches the use case actually has: the row exists and we sign a URL with the
 * stored object key (never a client-supplied one), or the row is missing and we surface a typed
 * {@link DocumentNotFoundException} that the web advice maps to 404 (ADR-0006). The TTL is whatever
 * the use case was constructed with — the controller has no say.
 */
@ExtendWith(MockitoExtension.class)
class GetDownloadUrlUseCaseTest {

  private static final Duration TTL = Duration.ofMinutes(15);

  @Mock private DocumentRepository repository;
  @Mock private DocumentStoragePort storage;

  @Test
  void resolves_persisted_object_key_and_signs_with_configured_ttl() {
    UUID id = UUID.randomUUID();
    String objectKey = "alice/" + id + "__quarterly-report.pdf";
    Document stored =
        Document.rehydrate(
            id,
            "alice",
            "quarterly-report.pdf",
            objectKey,
            2048L,
            "application/pdf",
            Set.of(Tag.normalize("finance")),
            Instant.now());
    when(repository.findById(id)).thenReturn(Optional.of(stored));
    when(storage.presignedGet(objectKey, TTL)).thenReturn("https://signed.example/" + objectKey);

    String url = new GetDownloadUrlUseCase(repository, storage, TTL).execute(id);

    assertThat(url).isEqualTo("https://signed.example/" + objectKey);
    verify(storage).presignedGet(objectKey, TTL);
  }

  @Test
  void throws_document_not_found_when_repository_returns_empty() {
    UUID id = UUID.randomUUID();
    when(repository.findById(id)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> new GetDownloadUrlUseCase(repository, storage, TTL).execute(id))
        .isInstanceOf(DocumentNotFoundException.class)
        .extracting("documentId")
        .isEqualTo(id);

    verify(storage, never()).presignedGet(anyString(), any());
  }
}
