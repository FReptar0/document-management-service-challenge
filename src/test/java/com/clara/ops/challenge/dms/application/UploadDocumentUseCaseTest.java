package com.clara.ops.challenge.dms.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.clara.ops.challenge.dms.domain.Document;
import com.clara.ops.challenge.dms.domain.port.DocumentRepository;
import com.clara.ops.challenge.dms.domain.port.DocumentStoragePort;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UploadDocumentUseCaseTest {

  @Mock private DocumentStoragePort storage;
  @Mock private DocumentRepository repository;

  @Test
  void streams_to_storage_with_counted_size_and_persists_document() {
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    doAnswer(
            inv -> {
              InputStream s = inv.getArgument(0);
              s.transferTo(OutputStream.nullOutputStream());
              return null;
            })
        .when(storage)
        .put(any(), anyString(), anyString());
    UploadDocumentUseCase useCase = new UploadDocumentUseCase(storage, repository);

    byte[] payload = "%PDF-1.4 hello".getBytes();
    Document result =
        useCase.execute(
            new UploadDocumentUseCase.UploadCommand("alice", "Hello.pdf", Set.of("Finance")),
            new ByteArrayInputStream(payload),
            "application/pdf");

    String expectedKey = "alice/" + result.id() + "__hello.pdf";
    verify(storage).put(any(), eq(expectedKey), eq("application/pdf"));
    verify(repository)
        .save(
            argThat(
                d ->
                    d.fileSize() == payload.length
                        && d.userId().equals("alice")
                        && d.name().equals("Hello.pdf")
                        && d.tags().stream().anyMatch(t -> t.name().equals("finance"))));
    assertThat(result.fileSize()).isEqualTo(payload.length);
  }

  @Test
  void compensates_with_delete_when_repository_save_fails() {
    when(repository.save(any())).thenThrow(new RuntimeException("DB down"));
    UploadDocumentUseCase useCase = new UploadDocumentUseCase(storage, repository);

    assertThatThrownBy(
            () ->
                useCase.execute(
                    new UploadDocumentUseCase.UploadCommand("alice", "a.pdf", Set.of()),
                    new ByteArrayInputStream("x".getBytes()),
                    "application/pdf"))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("DB down");

    verify(storage).put(any(), anyString(), anyString());
    verify(storage).delete(anyString());
  }

  @Test
  void does_not_call_delete_when_storage_put_throws() {
    doThrow(new RuntimeException("MinIO down")).when(storage).put(any(), anyString(), anyString());
    UploadDocumentUseCase useCase = new UploadDocumentUseCase(storage, repository);

    assertThatThrownBy(
            () ->
                useCase.execute(
                    new UploadDocumentUseCase.UploadCommand("alice", "a.pdf", Set.of()),
                    new ByteArrayInputStream("x".getBytes()),
                    "application/pdf"))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("MinIO down");

    verify(storage, never()).delete(anyString());
    verify(repository, never()).save(any());
  }
}
