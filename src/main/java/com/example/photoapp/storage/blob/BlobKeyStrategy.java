package com.example.photoapp.storage.blob;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Single source of truth for blob keys. Layout:
 *
 * <pre>
 *   schools/{schoolId}/events/{eventId}/{photoId}.{ext}
 * </pre>
 *
 * Provider-neutral on purpose — every {@link BlobStore} implementation
 * (S3, Azure Blob, GCS, MinIO) shares this layout so a future migration
 * across providers can carry keys verbatim.
 *
 * UUIDs in path components are guaranteed safe (no slashes, no dots). The
 * extension is validated against a strict allow-list pattern so a malicious
 * caller can't smuggle path traversal or weird characters into the key.
 */
public final class BlobKeyStrategy {

    private static final Pattern VALID_EXTENSION = Pattern.compile("^[a-z0-9]{1,8}$");

    private BlobKeyStrategy() {}

    public static String photoKey(UUID schoolId, UUID eventId, UUID photoId, String extension) {
        if (schoolId == null || eventId == null || photoId == null) {
            throw new IllegalArgumentException("schoolId, eventId, and photoId must be non-null");
        }
        if (extension == null || !VALID_EXTENSION.matcher(extension).matches()) {
            throw new IllegalArgumentException(
                    "extension must match [a-z0-9]{1,8} — got: " + extension);
        }
        return "schools/" + schoolId + "/events/" + eventId + "/" + photoId + "." + extension;
    }
}
