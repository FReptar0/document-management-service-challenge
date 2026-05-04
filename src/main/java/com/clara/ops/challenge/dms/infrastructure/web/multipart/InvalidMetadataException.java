package com.clara.ops.challenge.dms.infrastructure.web.multipart;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when the {@code metadata} JSON part fails Jakarta Bean Validation. Wraps the violation
 * messages so the client gets actionable detail; the global advice (Phase 8) will surface it as RFC
 * 7807 ProblemDetail.
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidMetadataException extends RuntimeException {

  public InvalidMetadataException(String message) {
    super(message);
  }
}
