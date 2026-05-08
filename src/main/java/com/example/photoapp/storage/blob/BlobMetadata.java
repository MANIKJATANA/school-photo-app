package com.example.photoapp.storage.blob;

/**
 * Subset of object-store metadata that the application cares about. Returned
 * from {@link BlobStore#head} so callers can verify upload size / content type
 * after a presigned PUT.
 */
public record BlobMetadata(long sizeBytes, String contentType) {}
