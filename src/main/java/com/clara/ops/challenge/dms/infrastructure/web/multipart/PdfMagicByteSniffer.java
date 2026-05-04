package com.clara.ops.challenge.dms.infrastructure.web.multipart;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Reads the first 5 bytes of a stream and rejects the upload unless they spell {@code %PDF-}. After
 * verification the consumed prefix is rejoined onto the original stream so the storage adapter
 * still sees the full body. Heap cost is exactly 5 bytes.
 */
public final class PdfMagicByteSniffer {

  private static final byte[] MAGIC = "%PDF-".getBytes(StandardCharsets.US_ASCII);

  private PdfMagicByteSniffer() {}

  public static InputStream verifyAndPrepend(InputStream in) throws IOException {
    byte[] head = in.readNBytes(MAGIC.length);
    if (head.length < MAGIC.length || !Arrays.equals(head, MAGIC)) {
      throw new UnsupportedFileTypeException(
          "File does not appear to be a PDF (expected '%PDF-' header)");
    }
    return new SequenceInputStream(new ByteArrayInputStream(head), in);
  }
}
