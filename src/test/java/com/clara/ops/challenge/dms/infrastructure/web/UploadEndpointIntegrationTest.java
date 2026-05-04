package com.clara.ops.challenge.dms.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.clara.ops.challenge.dms.infrastructure.persistence.jpa.DocumentJpaRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
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
 * Full-stack integration test for {@code POST /document-management/upload}: real Tomcat, real
 * Postgres (via Testcontainers JDBC URL) and real MinIO (via {@link MinIOContainer}). Covers the
 * ADR-0011 multipart contract end-to-end, including ordering rules and size/type guards.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class UploadEndpointIntegrationTest {

  private static final String BOUNDARY = "----dmsTestBoundary" + UUID.randomUUID();

  @Container
  static MinIOContainer minio =
      new MinIOContainer("minio/minio:latest")
          .withUserName("integ-access")
          .withPassword("integ-secret-key1");

  @DynamicPropertySource
  static void minioProps(DynamicPropertyRegistry r) {
    r.add("app.minio.endpoint", minio::getS3URL);
    r.add("app.minio.public-endpoint", minio::getS3URL);
    r.add("app.minio.access-key", minio::getUserName);
    r.add("app.minio.secret-key", minio::getPassword);
    r.add("app.minio.bucket", () -> "integ-bucket-" + UUID.randomUUID());
    r.add("app.upload.max-size-bytes", () -> 10_000_000); // 10 MB cap for this suite
    r.add("app.upload.enforce-pdf-magic", () -> true);
  }

  @LocalServerPort int port;

  @Autowired DocumentJpaRepository jpa;
  @Autowired MinioClient minioClient;
  @Autowired com.clara.ops.challenge.dms.infrastructure.storage.MinioProperties minioProperties;

  private final HttpClient http = HttpClient.newHttpClient();
  private final ObjectMapper json = new ObjectMapper();

  @BeforeAll
  static void boot() {
    minio.start();
  }

  @Test
  void uploads_a_pdf_persists_metadata_and_stores_object() throws Exception {
    byte[] pdfPayload = "%PDF-1.4 hello integration".getBytes(StandardCharsets.UTF_8);
    byte[] body =
        multipartBody(
            "{\"user\":\"alice\",\"name\":\"Hello.pdf\",\"tags\":[\"finance\",\"q3\"]}",
            pdfPayload);

    HttpResponse<String> response = post(body);

    assertThat(response.statusCode()).isEqualTo(201);
    JsonNode out = json.readTree(response.body());
    UUID id = UUID.fromString(out.get("id").asText());
    assertThat(out.get("user").asText()).isEqualTo("alice");
    assertThat(out.get("name").asText()).isEqualTo("Hello.pdf");
    assertThat(out.get("size").asLong()).isEqualTo(pdfPayload.length);
    assertThat(out.get("type").asText()).isEqualTo("application/pdf");
    assertThat(jpa.findById(id)).as("document persisted").isPresent();

    String objectKey = jpa.findById(id).orElseThrow().getObjectKey();
    try (InputStream got =
        minioClient.getObject(
            GetObjectArgs.builder().bucket(minioProperties.bucket()).object(objectKey).build())) {
      assertThat(got.readAllBytes()).isEqualTo(pdfPayload);
    }
  }

  @Test
  void rejects_with_400_when_file_arrives_before_metadata() throws Exception {
    byte[] body =
        multipartBody(
            new Part("file", "f.pdf", "application/pdf", "%PDF-1.4 oops".getBytes()),
            new Part(
                "metadata",
                null,
                "application/json",
                "{\"user\":\"alice\",\"name\":\"x.pdf\",\"tags\":[]}".getBytes()));
    assertThat(post(body).statusCode()).isEqualTo(400);
  }

  @Test
  void rejects_with_400_when_metadata_part_is_missing() throws Exception {
    byte[] body =
        multipartBody(new Part("file", "f.pdf", "application/pdf", "%PDF-1.4 lonely".getBytes()));
    assertThat(post(body).statusCode()).isEqualTo(400);
  }

  @Test
  void rejects_with_400_when_file_part_is_missing() throws Exception {
    byte[] body =
        multipartBody(
            new Part(
                "metadata",
                null,
                "application/json",
                "{\"user\":\"alice\",\"name\":\"x.pdf\",\"tags\":[]}".getBytes()));
    assertThat(post(body).statusCode()).isEqualTo(400);
  }

  @Test
  void rejects_with_400_when_metadata_user_is_blank() throws Exception {
    byte[] body =
        multipartBody("{\"user\":\"\",\"name\":\"x.pdf\",\"tags\":[]}", "%PDF-1.4 x".getBytes());
    assertThat(post(body).statusCode()).isEqualTo(400);
  }

  @Test
  void rejects_with_415_when_file_does_not_start_with_pdf_signature() throws Exception {
    byte[] body =
        multipartBody(
            "{\"user\":\"alice\",\"name\":\"x.pdf\",\"tags\":[]}",
            "<html>not a pdf</html>".getBytes());
    assertThat(post(body).statusCode()).isEqualTo(415);
  }

  @Test
  void rejects_with_413_when_payload_exceeds_max_size() throws Exception {
    byte[] huge = new byte[12_000_000]; // > 10 MB cap
    huge[0] = '%';
    huge[1] = 'P';
    huge[2] = 'D';
    huge[3] = 'F';
    huge[4] = '-';
    byte[] body = multipartBody("{\"user\":\"alice\",\"name\":\"big.pdf\",\"tags\":[]}", huge);
    assertThat(post(body).statusCode()).isEqualTo(413);
  }

  // ---------- helpers ----------

  private HttpResponse<String> post(byte[] body) throws Exception {
    HttpRequest req =
        HttpRequest.newBuilder(
                URI.create("http://localhost:" + port + "/document-management/upload"))
            .header("Content-Type", "multipart/form-data; boundary=" + BOUNDARY)
            .POST(HttpRequest.BodyPublishers.ofByteArray(body))
            .build();
    return http.send(req, HttpResponse.BodyHandlers.ofString());
  }

  private static byte[] multipartBody(String metadataJson, byte[] filePayload) throws Exception {
    return multipartBody(
        new Part(
            "metadata", null, "application/json", metadataJson.getBytes(StandardCharsets.UTF_8)),
        new Part("file", "doc.pdf", "application/pdf", filePayload));
  }

  private static byte[] multipartBody(Part... parts) throws Exception {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    for (Part p : parts) {
      out.write(("--" + BOUNDARY + "\r\n").getBytes(StandardCharsets.US_ASCII));
      String disposition =
          p.filename == null
              ? "Content-Disposition: form-data; name=\"" + p.fieldName + "\""
              : "Content-Disposition: form-data; name=\""
                  + p.fieldName
                  + "\"; filename=\""
                  + p.filename
                  + "\"";
      out.write((disposition + "\r\n").getBytes(StandardCharsets.US_ASCII));
      out.write(
          ("Content-Type: " + p.contentType + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
      out.write(p.body);
      out.write("\r\n".getBytes(StandardCharsets.US_ASCII));
    }
    out.write(("--" + BOUNDARY + "--\r\n").getBytes(StandardCharsets.US_ASCII));
    return out.toByteArray();
  }

  private record Part(String fieldName, String filename, String contentType, byte[] body) {}
}
