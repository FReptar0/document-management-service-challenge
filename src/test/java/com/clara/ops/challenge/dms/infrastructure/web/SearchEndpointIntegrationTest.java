package com.clara.ops.challenge.dms.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.clara.ops.challenge.dms.domain.Document;
import com.clara.ops.challenge.dms.domain.Tag;
import com.clara.ops.challenge.dms.domain.port.DocumentRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * Full-stack integration test for {@code POST /document-management/search}. Seeds documents via the
 * repository port (skipping the storage path entirely) so we can exercise the filter + pagination
 * matrix without paying the multipart cost on every test.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class SearchEndpointIntegrationTest {

  @Container
  static MinIOContainer minio =
      new MinIOContainer("minio/minio:latest")
          .withUserName("integ-search-access")
          .withPassword("integ-search-secret-key1");

  @DynamicPropertySource
  static void minioProps(DynamicPropertyRegistry r) {
    r.add("app.minio.endpoint", minio::getS3URL);
    r.add("app.minio.public-endpoint", minio::getS3URL);
    r.add("app.minio.access-key", minio::getUserName);
    r.add("app.minio.secret-key", minio::getPassword);
    r.add("app.minio.bucket", () -> "integ-search-bucket-" + UUID.randomUUID());
  }

  @LocalServerPort int port;

  @Autowired DocumentRepository repository;

  private final HttpClient http = HttpClient.newHttpClient();
  private final ObjectMapper json = new ObjectMapper();

  @BeforeAll
  static void boot() {
    minio.start();
  }

  @Test
  void returns_documents_filtered_by_user_and_paginated() throws Exception {
    String suite = UUID.randomUUID().toString();
    seed("alice-" + suite, "annual-report-2024.pdf", Set.of("finance"));
    seed("alice-" + suite, "weekly-summary.pdf", Set.of("ops"));
    seed("bob-" + suite, "alice-misled-bob.pdf", Set.of("finance"));

    HttpResponse<String> resp =
        post("/document-management/search?page=0&size=10", "{\"user\":\"alice-" + suite + "\"}");
    assertThat(resp.statusCode()).isEqualTo(200);
    JsonNode out = json.readTree(resp.body());

    assertThat(out.get("metadata").get("totalItems").asLong()).isEqualTo(2);
    assertThat(out.get("metadata").get("currentPage").asInt()).isZero();
    assertThat(out.get("metadata").get("itemsPerPage").asInt()).isEqualTo(10);
    assertThat(out.get("metadata").get("currentItems").asInt()).isEqualTo(2);
    JsonNode docs = out.get("documents");
    assertThat(docs.size()).isEqualTo(2);
    for (JsonNode doc : docs) {
      assertThat(doc.get("user").asText()).isEqualTo("alice-" + suite);
    }
  }

  @Test
  void filters_by_substring_in_name_case_insensitive() throws Exception {
    String suite = UUID.randomUUID().toString();
    seed("user-" + suite, "Quarterly-Report.pdf", Set.of());
    seed("user-" + suite, "weekly-summary.pdf", Set.of());

    HttpResponse<String> resp =
        post(
            "/document-management/search?page=0&size=10",
            "{\"user\":\"user-" + suite + "\",\"name\":\"REPORT\"}");
    assertThat(resp.statusCode()).isEqualTo(200);
    JsonNode out = json.readTree(resp.body());
    assertThat(out.get("metadata").get("totalItems").asLong()).isEqualTo(1);
    assertThat(out.get("documents").get(0).get("name").asText()).isEqualTo("Quarterly-Report.pdf");
  }

  @Test
  void filters_by_any_of_listed_tags() throws Exception {
    String suite = UUID.randomUUID().toString();
    seed("user-" + suite, "a.pdf", Set.of("finance"));
    seed("user-" + suite, "b.pdf", Set.of("ops"));
    seed("user-" + suite, "c.pdf", Set.of("legal"));

    HttpResponse<String> resp =
        post(
            "/document-management/search?page=0&size=10",
            "{\"user\":\"user-" + suite + "\",\"tags\":[\"finance\",\"legal\"]}");
    assertThat(resp.statusCode()).isEqualTo(200);
    assertThat(json.readTree(resp.body()).get("metadata").get("totalItems").asLong()).isEqualTo(2);
  }

  @Test
  void pagination_carries_correct_metadata_on_last_page() throws Exception {
    String suite = UUID.randomUUID().toString();
    for (int i = 0; i < 7; i++) {
      seed("page-" + suite, "doc-" + i + ".pdf", Set.of());
    }
    HttpResponse<String> resp =
        post("/document-management/search?page=2&size=3", "{\"user\":\"page-" + suite + "\"}");
    assertThat(resp.statusCode()).isEqualTo(200);
    JsonNode meta = json.readTree(resp.body()).get("metadata");
    assertThat(meta.get("currentPage").asInt()).isEqualTo(2);
    assertThat(meta.get("itemsPerPage").asInt()).isEqualTo(3);
    assertThat(meta.get("currentItems").asInt()).isEqualTo(1);
    assertThat(meta.get("totalPages").asInt()).isEqualTo(3);
    assertThat(meta.get("totalItems").asLong()).isEqualTo(7);
  }

  @Test
  void empty_filter_body_returns_all_documents() throws Exception {
    String suite = UUID.randomUUID().toString();
    seed("u1-" + suite, "x.pdf", Set.of());
    seed("u2-" + suite, "y.pdf", Set.of());

    HttpResponse<String> resp = post("/document-management/search?page=0&size=100", "{}");
    assertThat(resp.statusCode()).isEqualTo(200);
    long total = json.readTree(resp.body()).get("metadata").get("totalItems").asLong();
    assertThat(total)
        .as("includes the two seeded docs (and possibly more from other tests)")
        .isGreaterThanOrEqualTo(2);
  }

  // ---------- helpers ----------

  private HttpResponse<String> post(String pathAndQuery, String jsonBody) throws Exception {
    HttpRequest req =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + pathAndQuery))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
            .build();
    return http.send(req, HttpResponse.BodyHandlers.ofString());
  }

  private void seed(String userId, String name, Set<String> tagNames) {
    Set<Tag> tags =
        tagNames.stream().map(Tag::normalize).collect(java.util.stream.Collectors.toSet());
    Document doc =
        Document.create(
            userId,
            name,
            userId + "/" + UUID.randomUUID() + "__" + name,
            42L,
            "application/pdf",
            tags);
    repository.save(doc);
  }
}
