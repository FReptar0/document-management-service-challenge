package com.clara.ops.challenge.dms.infrastructure.config;

import com.clara.ops.challenge.dms.application.SearchDocumentsUseCase;
import com.clara.ops.challenge.dms.application.UploadDocumentUseCase;
import com.clara.ops.challenge.dms.domain.port.DocumentRepository;
import com.clara.ops.challenge.dms.domain.port.DocumentStoragePort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring wiring for the application-layer use cases. Use cases stay framework-free (ADR-0001); this
 * configuration provides their bean definitions in the infrastructure layer.
 */
@Configuration
public class UseCaseConfiguration {

  @Bean
  public UploadDocumentUseCase uploadDocumentUseCase(
      DocumentStoragePort storage, DocumentRepository repository) {
    return new UploadDocumentUseCase(storage, repository);
  }

  @Bean
  public SearchDocumentsUseCase searchDocumentsUseCase(DocumentRepository repository) {
    return new SearchDocumentsUseCase(repository);
  }
}
