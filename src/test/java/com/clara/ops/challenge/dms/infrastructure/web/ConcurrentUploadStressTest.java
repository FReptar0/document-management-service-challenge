package com.clara.ops.challenge.dms.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.clara.ops.challenge.dms.infrastructure.persistence.jpa.DocumentJpaRepository;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
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
 * pipeline holds under the README's hard concurrency constraint. Payloads are kept moderate (10 MB
 * each) so the suite finishes in a few seconds while still pushing the MinIO SDK part-buffer logic
 * past a single part boundary.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class ConcurrentUploadStressTest {

  private static final int CONCURRENCY = 10;
  private static final int PAYLOAD_BYTES = 10 * 1024 * 1024; // 10 MB
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
  void ten_parallel_uploads_all_succeed_and_persist_distinct_rows() throws Exception {
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
                    byte[] body = body("user-" + seed, "doc-" + seed + ".pdf");
                    HttpRequest req =
                        HttpRequest.newBuilder(
                                URI.create(
                                    "http://localhost:" + port + "/document-management/upload"))
                            .header("Content-Type", "multipart/form-data; boundary=" + BOUNDARY)
                            .timeout(Duration.ofMinutes(1))
                            .POST(HttpRequest.BodyPublishers.ofByteArray(body))
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

  private static byte[] body(String userId, String docName) throws Exception {
    byte[] file = new byte[PAYLOAD_BYTES];
    file[0] = '%';
    file[1] = 'P';
    file[2] = 'D';
    file[3] = 'F';
    file[4] = '-';
    String metadata =
        "{\"user\":\"" + userId + "\",\"name\":\"" + docName + "\",\"tags\":[\"stress\"]}";
    ByteArrayOutputStream out = new ByteArrayOutputStream(file.length + 512);
    appendPart(
        out, "metadata", null, "application/json", metadata.getBytes(StandardCharsets.UTF_8));
    appendPart(out, "file", docName, "application/pdf", file);
    out.write(("--" + BOUNDARY + "--\r\n").getBytes(StandardCharsets.US_ASCII));
    return out.toByteArray();
  }

  private static void appendPart(
      ByteArrayOutputStream out, String fieldName, String filename, String contentType, byte[] body)
      throws Exception {
    out.write(("--" + BOUNDARY + "\r\n").getBytes(StandardCharsets.US_ASCII));
    String disposition =
        filename == null
            ? "Content-Disposition: form-data; name=\"" + fieldName + "\""
            : "Content-Disposition: form-data; name=\""
                + fieldName
                + "\"; filename=\""
                + filename
                + "\"";
    out.write((disposition + "\r\n").getBytes(StandardCharsets.US_ASCII));
    out.write(("Content-Type: " + contentType + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
    out.write(body);
    out.write("\r\n".getBytes(StandardCharsets.US_ASCII));
  }
}
