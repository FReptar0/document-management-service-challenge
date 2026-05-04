package com.clara.ops.challenge.dms.infrastructure.web.advice;

import com.clara.ops.challenge.dms.domain.exception.DocumentNotFoundException;
import com.clara.ops.challenge.dms.infrastructure.web.multipart.InvalidMetadataException;
import com.clara.ops.challenge.dms.infrastructure.web.multipart.MissingMultipartPartException;
import com.clara.ops.challenge.dms.infrastructure.web.multipart.MultipartOrderViolationException;
import com.clara.ops.challenge.dms.infrastructure.web.multipart.MultipartParseException;
import com.clara.ops.challenge.dms.infrastructure.web.multipart.PayloadTooLargeException;
import com.clara.ops.challenge.dms.infrastructure.web.multipart.UnsupportedFileTypeException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;

/**
 * Global exception → {@link ProblemDetail} (RFC 7807) translator. Each handler returns the same
 * shape so clients can rely on it: {@code title}, {@code status}, {@code detail}, plus
 * problem-specific extensions under custom keys (e.g., {@code documentId}, {@code error}).
 *
 * <p>Distinct from the per-exception {@code @ResponseStatus} hints: those still apply when this
 * advice is bypassed, but the advice supersedes them and gives us a structured body. Logging is
 * intentionally terse — full stack-traces only at WARN+ for unexpected failures so the structured
 * log pipeline stays clean.
 */
@RestControllerAdvice
public class GlobalExceptionAdvice {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionAdvice.class);

  @ExceptionHandler(DocumentNotFoundException.class)
  public ProblemDetail handleDocumentNotFound(
      DocumentNotFoundException e, HttpServletRequest request) {
    ProblemDetail pd = problem(HttpStatus.NOT_FOUND, "Document not found", e.getMessage(), request);
    pd.setProperty("documentId", e.documentId().toString());
    return pd;
  }

  @ExceptionHandler({
    MultipartOrderViolationException.class,
    MultipartParseException.class,
    MissingMultipartPartException.class,
    InvalidMetadataException.class,
    IllegalArgumentException.class
  })
  public ProblemDetail handleBadRequest(RuntimeException e, HttpServletRequest request) {
    return problem(HttpStatus.BAD_REQUEST, "Bad request", e.getMessage(), request);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ProblemDetail handleBeanValidation(
      MethodArgumentNotValidException e, HttpServletRequest request) {
    String detail =
        e.getBindingResult().getFieldErrors().stream()
            .map(fe -> fe.getField() + " " + fe.getDefaultMessage())
            .reduce((a, b) -> a + "; " + b)
            .orElse(e.getMessage());
    return problem(HttpStatus.BAD_REQUEST, "Validation failed", detail, request);
  }

  @ExceptionHandler(PayloadTooLargeException.class)
  public ProblemDetail handlePayloadTooLarge(
      PayloadTooLargeException e, HttpServletRequest request) {
    return problem(HttpStatus.PAYLOAD_TOO_LARGE, "Payload too large", e.getMessage(), request);
  }

  @ExceptionHandler(UnsupportedFileTypeException.class)
  public ProblemDetail handleUnsupportedFileType(
      UnsupportedFileTypeException e, HttpServletRequest request) {
    return problem(
        HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported media type", e.getMessage(), request);
  }

  @ExceptionHandler(NoHandlerFoundException.class)
  public ProblemDetail handleNotFound(NoHandlerFoundException e, HttpServletRequest request) {
    return problem(HttpStatus.NOT_FOUND, "Endpoint not found", e.getMessage(), request);
  }

  @ExceptionHandler(Exception.class)
  public ProblemDetail handleUnexpected(Exception e, HttpServletRequest request) {
    log.warn("Unexpected error handling {} {}", request.getMethod(), request.getRequestURI(), e);
    return problem(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "Internal server error",
        "An unexpected error occurred. Reference the requestId in the response header.",
        request);
  }

  private static ProblemDetail problem(
      HttpStatus status, String title, String detail, HttpServletRequest request) {
    ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
    pd.setTitle(title);
    pd.setProperty("path", request.getRequestURI());
    String requestId = (String) request.getAttribute("requestId");
    if (requestId != null) {
      pd.setProperty("requestId", requestId);
    }
    return pd;
  }
}
