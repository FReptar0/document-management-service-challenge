package com.clara.ops.challenge.dms.infrastructure.web.multipart;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when the multipart request is missing one of the contract parts ({@code metadata} or
 * {@code file}) — the iterator finished before both were seen.
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class MissingMultipartPartException extends RuntimeException {

  public MissingMultipartPartException(String message) {
    super(message);
  }
}
