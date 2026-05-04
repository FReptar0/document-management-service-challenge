package com.clara.ops.challenge.dms.infrastructure.web.multipart;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when the {@code file} part fails the PDF magic-byte sniff (first 5 bytes != {@code
 * %PDF-}). Disabled by toggling {@code app.upload.enforce-pdf-magic=false}.
 */
@ResponseStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
public class UnsupportedFileTypeException extends RuntimeException {

  public UnsupportedFileTypeException(String message) {
    super(message);
  }
}
