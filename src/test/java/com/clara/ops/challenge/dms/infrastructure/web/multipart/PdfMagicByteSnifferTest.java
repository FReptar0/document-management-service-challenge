package com.clara.ops.challenge.dms.infrastructure.web.multipart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class PdfMagicByteSnifferTest {

  @Test
  void accepts_pdf_signature_and_returns_a_stream_that_yields_full_payload() throws Exception {
    byte[] payload = "%PDF-1.4 hello world".getBytes(StandardCharsets.US_ASCII);
    InputStream wrapped = PdfMagicByteSniffer.verifyAndPrepend(new ByteArrayInputStream(payload));

    byte[] read = wrapped.readAllBytes();
    assertThat(read).isEqualTo(payload);
  }

  @Test
  void rejects_when_first_bytes_are_not_pdf_signature() {
    byte[] payload = "<html>not a pdf</html>".getBytes(StandardCharsets.US_ASCII);
    assertThatThrownBy(
            () -> PdfMagicByteSniffer.verifyAndPrepend(new ByteArrayInputStream(payload)))
        .isInstanceOf(UnsupportedFileTypeException.class)
        .hasMessageContaining("PDF");
  }

  @Test
  void rejects_when_stream_is_shorter_than_signature() {
    byte[] payload = "%PDF".getBytes(StandardCharsets.US_ASCII); // 4 bytes, missing trailing dash
    assertThatThrownBy(
            () -> PdfMagicByteSniffer.verifyAndPrepend(new ByteArrayInputStream(payload)))
        .isInstanceOf(UnsupportedFileTypeException.class);
  }

  @Test
  void rejects_empty_stream() {
    assertThatThrownBy(
            () -> PdfMagicByteSniffer.verifyAndPrepend(new ByteArrayInputStream(new byte[0])))
        .isInstanceOf(UnsupportedFileTypeException.class);
  }
}
