package com.clara.ops.challenge.dms.infrastructure.web.multipart;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when the multipart envelope is malformed or unreadable (e.g., bad boundary, truncated
 * stream). Distinct from {@link MultipartOrderViolationException} so the global advice can return
 * different problem details.
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class MultipartParseException extends RuntimeException {

  public MultipartParseException(String message, Throwable cause) {
    super(message, cause);
  }
}
