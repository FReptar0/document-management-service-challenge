package com.clara.ops.challenge.dms.infrastructure.web;

import com.clara.ops.challenge.dms.application.GetDownloadUrlUseCase;
import com.clara.ops.challenge.dms.application.PageResult;
import com.clara.ops.challenge.dms.application.SearchCriteria;
import com.clara.ops.challenge.dms.application.SearchDocumentsUseCase;
import com.clara.ops.challenge.dms.application.UploadDocumentUseCase;
import com.clara.ops.challenge.dms.application.UploadDocumentUseCase.UploadCommand;
import com.clara.ops.challenge.dms.domain.Document;
import com.clara.ops.challenge.dms.infrastructure.web.dto.DocumentDownloadUrlResponse;
import com.clara.ops.challenge.dms.infrastructure.web.dto.DocumentResponse;
import com.clara.ops.challenge.dms.infrastructure.web.dto.DocumentSearchFiltersRequest;
import com.clara.ops.challenge.dms.infrastructure.web.dto.PaginatedDocumentSearchResponse;
import com.clara.ops.challenge.dms.infrastructure.web.dto.UploadMetadataRequest;
import com.clara.ops.challenge.dms.infrastructure.web.multipart.InvalidMetadataException;
import com.clara.ops.challenge.dms.infrastructure.web.multipart.MissingMultipartPartException;
import com.clara.ops.challenge.dms.infrastructure.web.multipart.MultipartOrderViolationException;
import com.clara.ops.challenge.dms.infrastructure.web.multipart.MultipartParseException;
import com.clara.ops.challenge.dms.infrastructure.web.multipart.MultipartStreamReader;
import com.clara.ops.challenge.dms.infrastructure.web.multipart.PayloadTooLargeException;
import com.clara.ops.challenge.dms.infrastructure.web.multipart.PdfMagicByteSniffer;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.apache.commons.fileupload2.core.FileItemInput;
import org.apache.commons.fileupload2.core.FileItemInputIterator;
import org.apache.commons.fileupload2.core.FileUploadException;
import org.apache.commons.fileupload2.core.FileUploadSizeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public HTTP entry point for the document-management API. The upload endpoint follows the
 * multipart contract documented in ADR-0011: {@code metadata} (application/json) must precede
 * {@code file} (application/pdf). Spring's default multipart resolver is disabled (ADR-0002); we
 * stream the raw request body through commons-fileupload2 so the heap stays bounded.
 */
@RestController
@RequestMapping("/document-management")
public class DocumentController {

  private static final Logger log = LoggerFactory.getLogger(DocumentController.class);

  private static final String METADATA_PART = "metadata";
  private static final String FILE_PART = "file";
  private static final String DEFAULT_FILE_CONTENT_TYPE = "application/pdf";

  private final UploadDocumentUseCase uploadUseCase;
  private final SearchDocumentsUseCase searchUseCase;
  private final GetDownloadUrlUseCase downloadUseCase;
  private final MultipartStreamReader multipartReader;
  private final ObjectMapper objectMapper;
  private final Validator validator;
  private final UploadProperties uploadProperties;

  public DocumentController(
      UploadDocumentUseCase uploadUseCase,
      SearchDocumentsUseCase searchUseCase,
      GetDownloadUrlUseCase downloadUseCase,
      MultipartStreamReader multipartReader,
      ObjectMapper objectMapper,
      Validator validator,
      UploadProperties uploadProperties) {
    this.uploadUseCase = uploadUseCase;
    this.searchUseCase = searchUseCase;
    this.downloadUseCase = downloadUseCase;
    this.multipartReader = multipartReader;
    this.objectMapper = objectMapper;
    this.validator = validator;
    this.uploadProperties = uploadProperties;
  }

  @GetMapping(value = "/download/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
  public DocumentDownloadUrlResponse download(@PathVariable("id") UUID id) {
    return new DocumentDownloadUrlResponse(downloadUseCase.execute(id));
  }

  @PostMapping(
      value = "/search",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public PaginatedDocumentSearchResponse search(
      @RequestBody(required = false) DocumentSearchFiltersRequest filters,
      @RequestParam(name = "page", defaultValue = "0") int page,
      @RequestParam(name = "size", defaultValue = "20") int size) {
    if (page < 0) {
      throw new IllegalArgumentException("page must be >= 0");
    }
    if (size < 1 || size > 100) {
      throw new IllegalArgumentException("size must be between 1 and 100");
    }
    DocumentSearchFiltersRequest f =
        filters != null ? filters : new DocumentSearchFiltersRequest(null, null, null);
    SearchCriteria criteria = new SearchCriteria(f.userId(), f.name(), f.tags());
    PageResult<Document> result = searchUseCase.execute(criteria, page, size);
    return PaginatedDocumentSearchResponse.from(result);
  }

  @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<DocumentResponse> upload(HttpServletRequest request) {
    if (!MultipartStreamReader.isMultipartContent(request)) {
      throw new MultipartParseException(
          "Request is not multipart/form-data",
          new IllegalStateException(request.getContentType()));
    }

    FileItemInputIterator parts = multipartReader.iterate(request, uploadProperties.maxSizeBytes());
    UploadMetadataRequest metadata = null;
    Document saved = null;

    try {
      while (parts.hasNext()) {
        FileItemInput part = parts.next();
        String fieldName = part.getFieldName();

        if (METADATA_PART.equals(fieldName) && part.isFormField()) {
          metadata = readAndValidateMetadata(part);
        } else if (FILE_PART.equals(fieldName) && !part.isFormField()) {
          if (metadata == null) {
            throw new MultipartOrderViolationException(
                "'metadata' part must arrive before 'file' (ADR-0011)");
          }
          saved = streamFileAndPersist(part, metadata);
        } else {
          log.debug(
              "Ignoring unexpected multipart part: name={} formField={}",
              fieldName,
              part.isFormField());
        }
      }
    } catch (FileUploadSizeException e) {
      throw new PayloadTooLargeException(e.getMessage());
    } catch (FileUploadException e) {
      throw new MultipartParseException("Failed to parse multipart envelope", e);
    } catch (IOException e) {
      throw new MultipartParseException("I/O error while reading multipart request", e);
    }

    if (metadata == null) {
      throw new MissingMultipartPartException("Missing required multipart part 'metadata'");
    }
    if (saved == null) {
      throw new MissingMultipartPartException("Missing required multipart part 'file'");
    }
    return ResponseEntity.status(HttpStatus.CREATED).body(DocumentResponse.from(saved));
  }

  private UploadMetadataRequest readAndValidateMetadata(FileItemInput part) throws IOException {
    UploadMetadataRequest dto;
    try (InputStream in = part.getInputStream()) {
      dto = objectMapper.readValue(in, UploadMetadataRequest.class);
    }
    Set<ConstraintViolation<UploadMetadataRequest>> violations = validator.validate(dto);
    if (!violations.isEmpty()) {
      String detail =
          violations.stream()
              .map(v -> v.getPropertyPath() + " " + v.getMessage())
              .collect(Collectors.joining("; "));
      throw new InvalidMetadataException("metadata validation failed: " + detail);
    }
    return dto;
  }

  private Document streamFileAndPersist(FileItemInput part, UploadMetadataRequest metadata)
      throws IOException {
    String contentType =
        part.getContentType() != null ? part.getContentType() : DEFAULT_FILE_CONTENT_TYPE;
    try (InputStream raw = part.getInputStream()) {
      InputStream content =
          uploadProperties.enforcePdfMagic() ? PdfMagicByteSniffer.verifyAndPrepend(raw) : raw;
      UploadCommand cmd = new UploadCommand(metadata.userId(), metadata.name(), metadata.tags());
      return uploadUseCase.execute(cmd, content, contentType);
    }
  }
}
