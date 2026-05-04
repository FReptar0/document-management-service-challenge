package com.clara.ops.challenge.dms.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * End-to-end happy path: upload a PDF, find it via search, fetch a presigned URL via download,
 * download the bytes from MinIO, and confirm the round-trip is byte-identical. Documents the
 * intended user journey and acts as a smoke test for the entire stack.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class EndToEndDocumentLifecycleTest {

  private static final String BOUNDARY = "----dmsE2E" + UUID.randomUUID();

  @Container
  static MinIOContainer minio =
      new MinIOContainer("minio/minio:latest")
          .withUserName("integ-e2e-access")
          .withPassword("integ-e2e-secret-key1");

  @DynamicPropertySource
  static void minioProps(DynamicPropertyRegistry r) {
    r.add("app.minio.endpoint", minio::getS3URL);
    r.add("app.minio.public-endpoint", minio::getS3URL);
    r.add("app.minio.access-key", minio::getUserName);
    r.add("app.minio.secret-key", minio::getPassword);
    r.add("app.minio.bucket", () -> "e2e-bucket-" + UUID.randomUUID());
    r.add("app.upload.enforce-pdf-magic", () -> true);
  }

  @LocalServerPort int port;

  private final HttpClient http = HttpClient.newHttpClient();
  private final ObjectMapper json = new ObjectMapper();

  @BeforeAll
  static void boot() {
    minio.start();
  }

  @Test
  void upload_search_download_roundtrip_returns_original_bytes() throws Exception {
    String suite = UUID.randomUUID().toString();
    String userId = "alice-e2e-" + suite;
    String name = "Quarterly Report.pdf";
    byte[] payload = "%PDF-1.4 e2e suite content".getBytes(StandardCharsets.UTF_8);

    // 1. Upload
    HttpResponse<String> uploadResp =
        http.send(
            HttpRequest.newBuilder(URI.create(base() + "/document-management/upload"))
                .header("Content-Type", "multipart/form-data; boundary=" + BOUNDARY)
                .POST(HttpRequest.BodyPublishers.ofByteArray(multipart(userId, name, payload)))
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(uploadResp.statusCode()).isEqualTo(201);
    UUID id = UUID.fromString(json.readTree(uploadResp.body()).get("id").asText());

    // 2. Search by user id, expect the new document in the page.
    HttpResponse<String> searchResp =
        http.send(
            HttpRequest.newBuilder(
                    URI.create(base() + "/document-management/search?page=0&size=10"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"user\":\"" + userId + "\"}"))
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(searchResp.statusCode()).isEqualTo(200);
    JsonNode searchOut = json.readTree(searchResp.body());
    assertThat(searchOut.get("metadata").get("totalItems").asLong()).isEqualTo(1);
    assertThat(searchOut.get("documents").get(0).get("id").asText()).isEqualTo(id.toString());

    // 3. Download yields a presigned URL.
    HttpResponse<String> downloadResp =
        http.send(
            HttpRequest.newBuilder(URI.create(base() + "/document-management/download/" + id))
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(downloadResp.statusCode()).isEqualTo(200);
    String url = json.readTree(downloadResp.body()).get("url").asText();

    // 4. Fetch the presigned URL — bytes should match the original.
    HttpResponse<byte[]> blob =
        http.send(
            HttpRequest.newBuilder(URI.create(url)).GET().build(),
            HttpResponse.BodyHandlers.ofByteArray());
    assertThat(blob.statusCode()).isEqualTo(200);
    assertThat(blob.body()).isEqualTo(payload);
  }

  private String base() {
    return "http://localhost:" + port;
  }

  private static byte[] multipart(String userId, String name, byte[] file) throws Exception {
    String metadata = "{\"user\":\"" + userId + "\",\"name\":\"" + name + "\",\"tags\":[\"e2e\"]}";
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    appendPart(
        out, "metadata", null, "application/json", metadata.getBytes(StandardCharsets.UTF_8));
    appendPart(out, "file", "doc.pdf", "application/pdf", file);
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
