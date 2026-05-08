package com.example.photoapp.config;

import com.example.photoapp.storage.blob.BlobStore;
import com.example.photoapp.storage.s3.S3BlobStore;
import com.example.photoapp.storage.s3.S3Properties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.S3Presigner.Builder;

import java.net.URI;
import java.time.Duration;

/**
 * S3 client + presigner beans, plus the {@link BlobStore} impl. The only
 * configuration class that imports the AWS SDK; everything downstream
 * consumes {@link BlobStore}.
 *
 * <p>Activated when {@code photoapp.storage.provider=s3} (the default). To
 * swap providers, implement {@link BlobStore} for the new backend, register
 * a sibling configuration class gated by the same property with a different
 * value (e.g. {@code havingValue = "azure"}), and flip the config. See
 * {@code docs/decisions/0009-blobstore-abstraction.md}.
 */
@Configuration
@ConditionalOnProperty(prefix = "photoapp.storage", name = "provider", havingValue = "s3", matchIfMissing = true)
public class S3Config {

    @Bean
    public S3Properties s3Properties(
            @Value("${photoapp.s3.bucket}") String bucket,
            @Value("${photoapp.s3.region}") String region,
            @Value("${photoapp.s3.endpoint:}") String endpoint,
            @Value("${photoapp.s3.path-style-access:true}") boolean pathStyle,
            @Value("${photoapp.s3.access-key}") String accessKey,
            @Value("${photoapp.s3.secret-key}") String secretKey,
            @Value("${photoapp.s3.put-url-ttl-seconds:600}") long putTtl,
            @Value("${photoapp.s3.get-url-ttl-seconds:300}") long getTtl) {
        return new S3Properties(bucket, region, endpoint, pathStyle, accessKey, secretKey,
                Duration.ofSeconds(putTtl), Duration.ofSeconds(getTtl));
    }

    @Bean
    public S3Client s3Client(S3Properties props) {
        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(props.region()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(props.accessKey(), props.secretKey())))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(props.pathStyleAccess())
                        .build());
        if (props.endpoint() != null && !props.endpoint().isBlank()) {
            builder.endpointOverride(URI.create(props.endpoint()));
        }
        return builder.build();
    }

    @Bean
    public S3Presigner s3Presigner(S3Properties props) {
        Builder builder = S3Presigner.builder()
                .region(Region.of(props.region()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(props.accessKey(), props.secretKey())))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(props.pathStyleAccess())
                        .build());
        if (props.endpoint() != null && !props.endpoint().isBlank()) {
            builder.endpointOverride(URI.create(props.endpoint()));
        }
        return builder.build();
    }

    @Bean
    public BlobStore blobStore(S3Client s3Client, S3Presigner presigner, S3Properties props) {
        return new S3BlobStore(s3Client, presigner, props.bucket());
    }
}
