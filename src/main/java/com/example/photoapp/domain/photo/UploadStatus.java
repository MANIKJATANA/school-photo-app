package com.example.photoapp.domain.photo;

/**
 * Upload lifecycle on {@link Photo}. Mirrors the V1 CHECK constraint on
 * {@code photo.upload_status}.
 *
 * <ul>
 *   <li>{@code PENDING} — row created via {@code POST /photos:initiate-upload};
 *       client has the presigned PUT URL but hasn't confirmed yet.</li>
 *   <li>{@code UPLOADED} — confirmed by {@code POST /photos/{id}:confirm-upload}
 *       after a successful HEAD against the object store.</li>
 *   <li>{@code FAILED} — the sweeper marked a stale PENDING row; or a confirm
 *       call detected a HEAD mismatch.</li>
 * </ul>
 */
public enum UploadStatus {
    PENDING,
    UPLOADED,
    FAILED
}
