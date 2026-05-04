package com.clara.ops.challenge.dms.infrastructure.web.multipart;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import org.apache.commons.fileupload2.core.FileItemInputIterator;
import org.apache.commons.fileupload2.core.FileUploadException;
import org.apache.commons.fileupload2.core.FileUploadSizeException;
import org.apache.commons.fileupload2.jakarta.servlet6.JakartaServletFileUpload;

/**
 * Thin facade over {@code commons-fileupload2-jakarta-servlet6} (ADR-0002). Encapsulates the parser
 * so the rest of the codebase never imports the library directly — keeps the dependency swap-out
 * path open if a future MinIO/Tomcat combo demands it.
 */
public final class MultipartStreamReader {

  /**
   * Returns an iterator over the multipart parts. {@code maxSizeBytes} caps the total request body;
   * the iterator will throw a {@link FileUploadSizeException} (translated downstream into a {@link
   * PayloadTooLargeException}) once that threshold is exceeded — covers both Content-Length-known
   * and chunked transfers.
   */
  public FileItemInputIterator iterate(HttpServletRequest request, long maxSizeBytes) {
    try {
      JakartaServletFileUpload<?, ?> upload = new JakartaServletFileUpload<>();
      upload.setSizeMax(maxSizeBytes);
      upload.setFileSizeMax(maxSizeBytes);
      return upload.getItemIterator(request);
    } catch (FileUploadSizeException e) {
      throw new PayloadTooLargeException(e.getMessage());
    } catch (FileUploadException e) {
      throw new MultipartParseException("Failed to parse multipart envelope", e);
    } catch (IOException e) {
      throw new MultipartParseException("I/O error while reading multipart request", e);
    }
  }

  public static boolean isMultipartContent(HttpServletRequest request) {
    return JakartaServletFileUpload.isMultipartContent(request);
  }
}
