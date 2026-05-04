package com.clara.ops.challenge.dms.infrastructure.web;

import com.clara.ops.challenge.dms.infrastructure.web.multipart.MultipartStreamReader;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires web-layer collaborators that don't need to be picked up by component-scan: the multipart
 * reader and the {@link UploadProperties} record. Keeps the {@code @Bean} style consistent with
 * {@code MinioConfiguration} and {@code UseCaseConfiguration}.
 */
@Configuration
@EnableConfigurationProperties(UploadProperties.class)
public class WebConfiguration {

  @Bean
  public MultipartStreamReader multipartStreamReader() {
    return new MultipartStreamReader();
  }
}
