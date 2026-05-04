package com.clara.ops.challenge.dms.infrastructure.web.multipart;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when the multipart parts arrive out of the contract order — typically the {@code file}
 * part is encountered before {@code metadata} has been fully read (ADR-0011).
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class MultipartOrderViolationException extends RuntimeException {

  public MultipartOrderViolationException(String message) {
    super(message);
  }
}
