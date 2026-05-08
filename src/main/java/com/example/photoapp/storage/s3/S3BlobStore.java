package com.example.photoapp.storage.s3;

import com.example.photoapp.storage.blob.BlobMetadata;
import com.example.photoapp.storage.blob.BlobStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Optional;

/**
 * AWS S3 implementation of {@link BlobStore}. The only place in the codebase
 * that imports {@code software.amazon.awssdk.services.s3.*}.
 */
public class S3BlobStore implements BlobStore {

    private static final Logger log = LoggerFactory.getLogger(S3BlobStore.class);

    private final S3Client client;
    private final S3Presigner presigner;
    private final String bucket;

    public S3BlobStore(S3Client client, S3Presigner presigner, String bucket) {
        this.client = client;
        this.presigner = presigner;
        this.bucket = bucket;
    }

    @Override
    public URI presignPut(String key, String contentType, Duration ttl) {
        PutObjectRequest put = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .build();
        URI uri = toUri(presigner.presignPutObject(PutObjectPresignRequest.builder()
                        .signatureDuration(ttl)
                        .putObjectRequest(put)
                        .build())
                .url());
        log.debug("presigned PUT bucket={} key={} ttl={}", bucket, key, ttl);
        return uri;
    }

    @Override
    public URI presignGet(String key, Duration ttl) {
        GetObjectRequest get = GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();
        URI uri = toUri(presigner.presignGetObject(GetObjectPresignRequest.builder()
                        .signatureDuration(ttl)
                        .getObjectRequest(get)
                        .build())
                .url());
        log.debug("presigned GET bucket={} key={} ttl={}", bucket, key, ttl);
        return uri;
    }

    private static URI toUri(java.net.URL url) {
        try {
            return url.toURI();
        } catch (URISyntaxException e) {
            // The presigner only ever produces well-formed URLs; this branch is
            // for the compiler — if it ever fires, it's a bug in the SDK.
            throw new IllegalStateException("S3 presigner produced malformed URL", e);
        }
    }

    @Override
    public Optional<BlobMetadata> head(String key) {
        try {
            HeadObjectResponse resp = client.headObject(HeadObjectRequest.builder()
                    .bucket(bucket).key(key).build());
            return Optional.of(new BlobMetadata(resp.contentLength(), resp.contentType()));
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        } catch (S3Exception e) {
            // S3 returns 404 with a different error class shape via AWS SDK v2.
            if (e.statusCode() == 404) {
                return Optional.empty();
            }
            throw e;
        }
    }

    @Override
    public void delete(String key) {
        client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
        log.debug("deleted bucket={} key={}", bucket, key);
    }
}
