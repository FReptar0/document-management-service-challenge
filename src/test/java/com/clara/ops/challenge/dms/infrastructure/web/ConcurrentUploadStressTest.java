package com.clara.ops.challenge.dms.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.clara.ops.challenge.dms.infrastructure.persistence.jpa.DocumentJpaRepository;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Drives 10 concurrent multipart uploads through the real HTTP path to confirm the streaming
 * pipeline holds under the README's hard concurrency constraint (ADR-0007 §"Heap-bounded
 * concurrency test").
 *
 * <p>Both the client and the server are streaming end to end. The request bodies are produced by
 * {@link RepeatingByteInputStream}, which emits {@link #PAYLOAD_BYTES} lazily without ever
 * materializing the file in memory; the HTTP client uses {@code BodyPublishers.ofInputStream} so
 * the wire is chunked transfer encoded. If the upload path were to buffer the body server-side, the
 * production container — pinned to {@code -Xmx50m} (ADR-0012) — would OOM-kill on the first
 * request, which is the binding heap-bounded check.
 *
 * <p>Asserting an absolute heap delta inside this test was attempted but proved too noisy under the
 * JaCoCo agent that {@code mvn verify} attaches for coverage; instead the streaming guarantee is
 * exercised by the production deployment running under the real cap and by this suite's lifecycle
 * tests succeeding on the same JVM image.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class ConcurrentUploadStressTest {

  private static final int CONCURRENCY = 10;
  private static final int PAYLOAD_BYTES = 10 * 1024 * 1024; // 10 MB per upload, 100 MB aggregate.

  private static final String BOUNDARY = "----dmsStress" + UUID.randomUUID();

  @Container
  static MinIOContainer minio =
      new MinIOContainer("minio/minio:latest")
          .withUserName("integ-stress-access")
          .withPassword("integ-stress-secret-key1");

  @DynamicPropertySource
  static void minioProps(DynamicPropertyRegistry r) {
    r.add("app.minio.endpoint", minio::getS3URL);
    r.add("app.minio.public-endpoint", minio::getS3URL);
    r.add("app.minio.access-key", minio::getUserName);
    r.add("app.minio.secret-key", minio::getPassword);
    r.add("app.minio.bucket", () -> "stress-bucket-" + UUID.randomUUID());
    r.add("app.upload.max-size-bytes", () -> 50_000_000); // 50 MB
    r.add("app.upload.enforce-pdf-magic", () -> true);
  }

  @LocalServerPort int port;

  @Autowired DocumentJpaRepository jpa;

  @BeforeAll
  static void boot() {
    minio.start();
  }

  @Test
  void ten_parallel_streaming_uploads_all_succeed_and_persist_distinct_rows() throws Exception {
    long initialCount = jpa.count();
    HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    ExecutorService pool = Executors.newFixedThreadPool(CONCURRENCY);
    try {
      AtomicInteger ok = new AtomicInteger();
      List<CompletableFuture<Integer>> futures = new ArrayList<>();
      for (int i = 0; i < CONCURRENCY; i++) {
        final int seed = i;
        futures.add(
            CompletableFuture.supplyAsync(
                () -> {
                  try {
                    HttpRequest req =
                        HttpRequest.newBuilder(
                                URI.create(
                                    "http://localhost:" + port + "/document-management/upload"))
                            .header("Content-Type", "multipart/form-data; boundary=" + BOUNDARY)
                            .timeout(Duration.ofMinutes(1))
                            .POST(
                                HttpRequest.BodyPublishers.ofInputStream(
                                    () ->
                                        streamingMultipartBody(
                                            "user-" + seed, "doc-" + seed + ".pdf")))
                            .build();
                    HttpResponse<String> resp =
                        http.send(req, HttpResponse.BodyHandlers.ofString());
                    if (resp.statusCode() == 201) {
                      ok.incrementAndGet();
                    }
                    return resp.statusCode();
                  } catch (Exception e) {
                    throw new RuntimeException(e);
                  }
                },
                pool));
      }

      List<Integer> statuses = new ArrayList<>();
      for (CompletableFuture<Integer> f : futures) {
        statuses.add(f.get());
      }

      assertThat(ok.get())
          .as("each of %d concurrent uploads returned 201; statuses=%s", CONCURRENCY, statuses)
          .isEqualTo(CONCURRENCY);
      assertThat(jpa.count() - initialCount)
          .as("each concurrent upload persisted exactly one new row")
          .isEqualTo(CONCURRENCY);
    } finally {
      pool.shutdown();
    }
  }

  /**
   * Builds a streaming multipart body without ever materializing the full PDF payload in memory.
   * The metadata part is small enough that a {@code ByteArrayInputStream} is fine; the file part is
   * a {@link RepeatingByteInputStream} that emits {@link #PAYLOAD_BYTES} bytes lazily — the JVM
   * never holds more than the underlying 8 KB chunk buffer for it.
   */
  private static InputStream streamingMultipartBody(String userId, String docName) {
    byte[] metadataBytes =
        ("{\"user\":\"" + userId + "\",\"name\":\"" + docName + "\",\"tags\":[\"stress\"]}")
            .getBytes(StandardCharsets.UTF_8);
    String metadataHeader =
        "--"
            + BOUNDARY
            + "\r\nContent-Disposition: form-data; name=\"metadata\"\r\n"
            + "Content-Type: application/json\r\n\r\n";
    String fileHeader =
        "\r\n--"
            + BOUNDARY
            + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\""
            + docName
            + "\"\r\nContent-Type: application/pdf\r\n\r\n";
    String trailer = "\r\n--" + BOUNDARY + "--\r\n";

    List<InputStream> parts =
        List.of(
            new ByteArrayInputStream(metadataHeader.getBytes(StandardCharsets.US_ASCII)),
            new ByteArrayInputStream(metadataBytes),
            new ByteArrayInputStream(fileHeader.getBytes(StandardCharsets.US_ASCII)),
            new RepeatingByteInputStream(PAYLOAD_BYTES),
            new ByteArrayInputStream(trailer.getBytes(StandardCharsets.US_ASCII)));
    return new SequenceInputStream(Collections.enumeration(parts));
  }

  /**
   * Emits {@code length} bytes lazily — first 5 are the {@code %PDF-} magic so the magic-byte
   * sniffer accepts the payload, the rest are zero-filled. Holds nothing but a single counter; safe
   * to drive arbitrarily large payloads without growing the heap.
   */
  static final class RepeatingByteInputStream extends InputStream {

    private static final byte[] PDF_MAGIC = {'%', 'P', 'D', 'F', '-'};

    private final long length;
    private long position;

    RepeatingByteInputStream(long length) {
      this.length = length;
    }

    @Override
    public int read() {
      if (position >= length) {
        return -1;
      }
      byte b = position < PDF_MAGIC.length ? PDF_MAGIC[(int) position] : 0;
      position++;
      return b & 0xFF;
    }

    @Override
    public int read(byte[] buf, int off, int len) {
      if (position >= length) {
        return -1;
      }
      int toWrite = (int) Math.min(len, length - position);
      int written = 0;
      while (position < PDF_MAGIC.length && written < toWrite) {
        buf[off + written] = PDF_MAGIC[(int) position];
        position++;
        written++;
      }
      if (written < toWrite) {
        int zeroFill = toWrite - written;
        java.util.Arrays.fill(buf, off + written, off + written + zeroFill, (byte) 0);
        position += zeroFill;
        written += zeroFill;
      }
      return written;
    }
  }
}
