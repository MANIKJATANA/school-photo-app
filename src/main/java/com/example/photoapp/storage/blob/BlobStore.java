package com.example.photoapp.storage.blob;

import java.net.URI;
import java.time.Duration;
import java.util.Optional;

/**
 * Object-storage seam. Services consume this; only the S3 implementation
 * touches the AWS SDK. Swapping to MinIO or GCS in future is one new impl
 * + a config bean — no service-layer changes.
 *
 * Per ADR 0005, the API never proxies image bytes; uploads and downloads
 * always go via presigned URLs returned by {@link #presignPut} and
 * {@link #presignGet}. The server only ever calls {@link #head} (to verify
 * an upload after the client confirms) and {@link #delete} (sweeper / hard
 * delete, when allowed).
 */
public interface BlobStore {

    URI presignPut(String key, String contentType, Duration ttl);

    URI presignGet(String key, Duration ttl);

    /** Returns metadata if the object exists, empty if it doesn't. Never throws on missing. */
    Optional<BlobMetadata> head(String key);

    /** No-op if the object doesn't exist. */
    void delete(String key);
}
