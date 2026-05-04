package com.clara.ops.challenge.dms.infrastructure.storage;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Creates the configured bucket on application startup if it does not already exist. Runs once per
 * boot via {@link ApplicationRunner}. Idempotent.
 */
@Component
public class MinioBucketBootstrap implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(MinioBucketBootstrap.class);

  private final MinioClient client;
  private final MinioProperties props;

  public MinioBucketBootstrap(MinioClient client, MinioProperties props) {
    this.client = client;
    this.props = props;
  }

  @Override
  public void run(ApplicationArguments args) throws Exception {
    boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(props.bucket()).build());
    if (exists) {
      log.info("MinIO bucket '{}' already present", props.bucket());
      return;
    }
    client.makeBucket(MakeBucketArgs.builder().bucket(props.bucket()).build());
    log.info("Created MinIO bucket '{}'", props.bucket());
  }
}
