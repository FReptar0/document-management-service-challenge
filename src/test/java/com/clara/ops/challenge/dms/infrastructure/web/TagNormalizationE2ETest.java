package com.clara.ops.challenge.dms.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.clara.ops.challenge.dms.infrastructure.persistence.jpa.TagJpaRepository;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Verifies the documented tag-normalization contract end-to-end (ADR-0009): three uploads carrying
 * the same logical tag in different surface forms ({@code Finance}, {@code FINANCE}, {@code "
 * finance "}) collapse into a single canonical {@code finance} row, and the search endpoint returns
 * all three documents when filtered by that canonical name.
 *
 * <p>Pinpointing this in a single E2E test makes the normalize + race-safe upsert behavior obvious
 * to a reviewer without forcing them to chase the unit-level coverage.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class TagNormalizationE2ETest {

  private static final String BOUNDARY = "----dmsTagNorm" + UUID.randomUUID();

  @Container
  static MinIOContainer minio =
      new MinIOContainer("minio/minio:latest")
          .withUserName("integ-tag-access")
          .withPassword("integ-tag-secret-key1");

  @DynamicPropertySource
  static void minioProps(DynamicPropertyRegistry r) {
    r.add("app.minio.endpoint", minio::getS3URL);
    r.add("app.minio.public-endpoint", minio::getS3URL);
    r.add("app.minio.access-key", minio::getUserName);
    r.add("app.minio.secret-key", minio::getPassword);
    r.add("app.minio.bucket", () -> "tag-norm-bucket-" + UUID.randomUUID());
    r.add("app.upload.enforce-pdf-magic", () -> true);
  }

  @LocalServerPort int port;

  @Autowired TagJpaRepository tagJpa;

  private final HttpClient http = HttpClient.newHttpClient();
  private final ObjectMapper json = new ObjectMapper();

  @BeforeAll
  static void boot() {
    minio.start();
  }

  @Test
  void equivalent_tag_surface_forms_collapse_to_one_row_and_match_via_search() throws Exception {
    String suite = UUID.randomUUID().toString();
    String userId = "tagger-" + suite;

    String[] surfaceForms = {"Finance", "FINANCE", "  finance  "};
    for (int i = 0; i < surfaceForms.length; i++) {
      assertThat(uploadOne(userId, "doc-" + i + ".pdf", surfaceForms[i])).isEqualTo(201);
    }

    long financeRows = tagJpa.findAll().stream().filter(t -> "finance".equals(t.getName())).count();
    assertThat(financeRows)
        .as(
            "normalize() + INSERT ... ON CONFLICT DO NOTHING converged on exactly one 'finance'"
                + " row")
        .isOne();

    HttpResponse<String> searchResp =
        http.send(
            HttpRequest.newBuilder(
                    URI.create(
                        "http://localhost:" + port + "/document-management/search?page=0&size=10"))
                .header("Content-Type", "application/json")
                .POST(
                    HttpRequest.BodyPublishers.ofString(
                        "{\"user\":\"" + userId + "\",\"tags\":[\"finance\"]}"))
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(searchResp.statusCode()).isEqualTo(200);
    JsonNode meta = json.readTree(searchResp.body()).get("metadata");
    assertThat(meta.get("totalItems").asLong())
        .as("all three documents match the canonical 'finance' tag")
        .isEqualTo(3);
  }

  private int uploadOne(String userId, String name, String surfaceTag) throws Exception {
    byte[] file = "%PDF-1.4 sample".getBytes(StandardCharsets.UTF_8);
    String metadata =
        "{\"user\":\""
            + userId
            + "\",\"name\":\""
            + name
            + "\",\"tags\":[\""
            + escape(surfaceTag)
            + "\"]}";
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    appendPart(
        out, "metadata", null, "application/json", metadata.getBytes(StandardCharsets.UTF_8));
    appendPart(out, "file", "doc.pdf", "application/pdf", file);
    out.write(("--" + BOUNDARY + "--\r\n").getBytes(StandardCharsets.US_ASCII));

    return http.send(
            HttpRequest.newBuilder(
                    URI.create("http://localhost:" + port + "/document-management/upload"))
                .header("Content-Type", "multipart/form-data; boundary=" + BOUNDARY)
                .POST(HttpRequest.BodyPublishers.ofByteArray(out.toByteArray()))
                .build(),
            HttpResponse.BodyHandlers.ofString())
        .statusCode();
  }

  private static String escape(String s) {
    return s.replace("\\", "\\\\").replace("\"", "\\\"");
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
