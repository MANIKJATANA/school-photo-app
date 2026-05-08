package com.example.photoapp.storage.s3;

import com.example.photoapp.storage.blob.BlobMetadata;
import com.example.photoapp.storage.blob.BlobStore;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.testcontainers.containers.localstack.LocalStackContainer.Service.S3;

/**
 * LocalStack-backed integration test for {@link S3BlobStore}. Docker-skipped
 * here; runs on dev machines with a Docker daemon.
 */
@Testcontainers(disabledWithoutDocker = true)
class S3BlobStoreTest {

    private static final String BUCKET = "test-bucket-" + UUID.randomUUID();

    static LocalStackContainer localstack;
    static S3Client s3;
    static S3Presigner presigner;
    static BlobStore store;

    @BeforeAll
    static void setUp() {
        localstack = new LocalStackContainer(DockerImageName.parse("localstack/localstack:3.8"))
                .withServices(S3);
        localstack.start();

        URI endpoint = localstack.getEndpointOverride(S3);
        AwsBasicCredentials creds = AwsBasicCredentials.create(
                localstack.getAccessKey(), localstack.getSecretKey());
        S3Configuration cfg = S3Configuration.builder().pathStyleAccessEnabled(true).build();

        s3 = S3Client.builder()
                .endpointOverride(endpoint)
                .region(Region.of(localstack.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(creds))
                .serviceConfiguration(cfg)
                .build();
        presigner = S3Presigner.builder()
                .endpointOverride(endpoint)
                .region(Region.of(localstack.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(creds))
                .serviceConfiguration(cfg)
                .build();

        s3.createBucket(CreateBucketRequest.builder().bucket(BUCKET).build());
        store = new S3BlobStore(s3, presigner, BUCKET);
    }

    @AfterAll
    static void tearDown() {
        if (presigner != null) presigner.close();
        if (s3 != null) s3.close();
        if (localstack != null) localstack.stop();
    }

    @Test
    void presign_put_then_get_round_trip() throws Exception {
        String key = "round-trip/" + UUID.randomUUID() + ".bin";
        byte[] payload = "hello, photoapp".getBytes();

        URI putUrl = store.presignPut(key, "application/octet-stream", Duration.ofMinutes(5));
        HttpResponse<Void> putResp = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(putUrl)
                        .header("Content-Type", "application/octet-stream")
                        .PUT(HttpRequest.BodyPublishers.ofByteArray(payload))
                        .build(),
                HttpResponse.BodyHandlers.discarding());
        assertThat(putResp.statusCode()).isBetween(200, 299);

        URI getUrl = store.presignGet(key, Duration.ofMinutes(5));
        HttpResponse<byte[]> getResp = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(getUrl).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertThat(getResp.statusCode()).isEqualTo(200);
        assertThat(getResp.body()).isEqualTo(payload);
    }

    @Test
    void head_returns_metadata_for_existing_object() throws IOException {
        String key = "head-existing/" + UUID.randomUUID() + ".bin";
        byte[] payload = new byte[]{1, 2, 3, 4, 5};
        s3.putObject(PutObjectRequest.builder().bucket(BUCKET).key(key)
                        .contentType("application/octet-stream").build(),
                RequestBody.fromBytes(payload));

        Optional<BlobMetadata> meta = store.head(key);

        assertThat(meta).isPresent();
        assertThat(meta.get().sizeBytes()).isEqualTo(payload.length);
        assertThat(meta.get().contentType()).isEqualTo("application/octet-stream");
    }

    @Test
    void head_returns_empty_for_missing_object() {
        assertThat(store.head("definitely-does-not-exist-" + UUID.randomUUID())).isEmpty();
    }

    @Test
    void delete_on_missing_object_does_not_throw() {
        store.delete("also-not-here-" + UUID.randomUUID());
    }
}
