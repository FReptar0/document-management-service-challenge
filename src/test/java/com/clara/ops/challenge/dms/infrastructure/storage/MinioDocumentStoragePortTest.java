package com.clara.ops.challenge.dms.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Integration test for {@link MinioDocumentStoragePort}. Plain JUnit + Testcontainers — no Spring
 * context — so the round-trip is fast and isolates the adapter from the rest of the application.
 *
 * <p>Both internal and public clients point at the same Testcontainers endpoint because the test
 * JVM is itself the HTTP client; in production the two endpoints differ (compose alias vs host).
 */
@Testcontainers
class MinioDocumentStoragePortTest {

  @Container static MinIOContainer minio = new MinIOContainer("minio/minio:latest");

  private static MinioDocumentStoragePort storage;
  private static String bucket;

  @BeforeAll
  static void setup() throws Exception {
    bucket = "test-bucket-" + UUID.randomUUID();
    MinioProperties props =
        new MinioProperties(
            minio.getS3URL(),
            minio.getS3URL(),
            minio.getUserName(),
            minio.getPassword(),
            bucket,
            new MinioProperties.Presigned(300));
    MinioClient client =
        MinioClient.builder()
            .endpoint(props.endpoint())
            .credentials(props.accessKey(), props.secretKey())
            .build();
    client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
    storage = new MinioDocumentStoragePort(client, client, props);
  }

  @Test
  void put_then_presigned_get_returns_same_bytes() throws Exception {
    byte[] payload = "%PDF-1.4 sample document content".getBytes(StandardCharsets.UTF_8);
    String objectKey = "alice/" + UUID.randomUUID() + "__sample.pdf";

    storage.put(new ByteArrayInputStream(payload), objectKey, "application/pdf");

    String url = storage.presignedGet(objectKey, Duration.ofMinutes(5));
    assertThat(url).startsWith(minio.getS3URL());

    HttpClient httpClient = HttpClient.newHttpClient();
    HttpResponse<byte[]> response =
        httpClient.send(
            HttpRequest.newBuilder(URI.create(url)).GET().build(),
            HttpResponse.BodyHandlers.ofByteArray());

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).isEqualTo(payload);
  }

  @Test
  void delete_swallows_missing_key() {
    String absentKey = "nobody/" + UUID.randomUUID() + ".pdf";
    assertThatNoException().isThrownBy(() -> storage.delete(absentKey));
  }
}
