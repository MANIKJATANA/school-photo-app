package com.example.photoapp.storage.s3;

import java.time.Duration;

/**
 * Configuration for {@link S3BlobStore} and the S3 client beans. Wired in
 * {@link com.example.photoapp.config.S3Config} from {@code photoapp.s3.*}
 * properties.
 */
public record S3Properties(
        String bucket,
        String region,
        String endpoint,
        boolean pathStyleAccess,
        String accessKey,
        String secretKey,
        Duration putUrlTtl,
        Duration getUrlTtl) {}
