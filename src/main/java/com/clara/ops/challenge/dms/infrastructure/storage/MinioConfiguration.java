package com.clara.ops.challenge.dms.infrastructure.storage;

import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Wires the MinIO SDK into Spring. Two clients exist on purpose:
 *
 * <ul>
 *   <li>{@code minioInternal} (default / {@link Primary}) — used for control-plane and data-plane
 *       calls between the service and MinIO over the compose network.
 *   <li>{@code minioPublic} (qualified) — used only to generate presigned URLs. The signature is
 *       computed against the URL host that the *client* will see, so we must sign with
 *       {@code app.minio.public-endpoint}, not the internal compose hostname.
 * </ul>
 */
@Configuration
@EnableConfigurationProperties(MinioProperties.class)
public class MinioConfiguration {

  @Bean
  @Primary
  public MinioClient minioInternal(MinioProperties props) {
    return MinioClient.builder()
        .endpoint(props.endpoint())
        .credentials(props.accessKey(), props.secretKey())
        .build();
  }

  @Bean
  @Qualifier("minioPublic")
  public MinioClient minioPublic(MinioProperties props) {
    return MinioClient.builder()
        .endpoint(props.publicEndpoint())
        .credentials(props.accessKey(), props.secretKey())
        .build();
  }
}
