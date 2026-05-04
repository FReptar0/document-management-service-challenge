package com.clara.ops.challenge.dms.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.clara.ops.challenge.dms.domain.Document;
import com.clara.ops.challenge.dms.domain.Tag;
import com.clara.ops.challenge.dms.domain.port.DocumentRepository;
import com.clara.ops.challenge.dms.domain.port.DocumentStoragePort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Set;
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
 * Full-stack integration test for {@code GET /document-management/download/{id}}: persists a
 * document via the repository, writes the bytes via the storage port, then fetches a presigned
 * URL through the HTTP API and confirms the URL serves the same payload.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class DownloadEndpointIntegrationTest {

  @Container
  static MinIOContainer minio =
      new MinIOContainer("minio/minio:latest")
          .withUserName("integ-download-access")
          .withPassword("integ-download-secret-key1");

  @DynamicPropertySource
  static void minioProps(DynamicPropertyRegistry r) {
    r.add("app.minio.endpoint", minio::getS3URL);
    r.add("app.minio.public-endpoint", minio::getS3URL);
    r.add("app.minio.access-key", minio::getUserName);
    r.add("app.minio.secret-key", minio::getPassword);
    r.add("app.minio.bucket", () -> "integ-download-bucket-" + UUID.randomUUID());
  }

  @LocalServerPort int port;

  @Autowired DocumentRepository repository;
  @Autowired DocumentStoragePort storage;

  private final HttpClient http = HttpClient.newHttpClient();
  private final ObjectMapper json = new ObjectMapper();

  @BeforeAll
  static void boot() {
    minio.start();
  }

  @Test
  void returns_200_with_presigned_url_that_serves_the_stored_bytes() throws Exception {
    byte[] payload = "%PDF-1.4 download me".getBytes(StandardCharsets.UTF_8);
    String userId = "alice";
    UUID documentId = UUID.randomUUID();
    String objectKey = userId + "/" + documentId + "__download-me.pdf";
    storage.put(new ByteArrayInputStream(payload), objectKey, "application/pdf");
    Document doc =
        Document.rehydrate(
            documentId,
            userId,
            "download-me.pdf",
            objectKey,
            payload.length,
            "application/pdf",
            Set.of(Tag.normalize("share")),
            java.time.Instant.now());
    repository.save(doc);

    HttpResponse<String> resp =
        http.send(
            HttpRequest.newBuilder(
                    URI.create("http://localhost:" + port + "/document-management/download/" + documentId))
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString());

    assertThat(resp.statusCode()).isEqualTo(200);
    JsonNode out = json.readTree(resp.body());
    String url = out.get("url").asText();
    assertThat(url).isNotBlank();

    HttpResponse<byte[]> blob =
        http.send(
            HttpRequest.newBuilder(URI.create(url)).GET().build(),
            HttpResponse.BodyHandlers.ofByteArray());
    assertThat(blob.statusCode()).isEqualTo(200);
    assertThat(blob.body()).isEqualTo(payload);
  }

  @Test
  void returns_404_when_document_does_not_exist() throws Exception {
    HttpResponse<String> resp =
        http.send(
            HttpRequest.newBuilder(
                    URI.create(
                        "http://localhost:" + port + "/document-management/download/" + UUID.randomUUID()))
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(resp.statusCode()).isEqualTo(404);
  }
}
