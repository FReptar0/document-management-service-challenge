package com.clara.ops.challenge.dms.infrastructure.web.multipart;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when the upload body exceeds {@code app.upload.max-size-bytes} — either the pre-flight
 * {@code Content-Length} check or the in-stream byte counter (covering chunked transfer encodings
 * that omit the header).
 */
@ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
public class PayloadTooLargeException extends RuntimeException {

  public PayloadTooLargeException(String message) {
    super(message);
  }
}
