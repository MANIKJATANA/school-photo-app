package com.example.photoapp.domain.photo;

/**
 * ML face-matching lifecycle on {@link Photo}. Mirrors the V1 CHECK constraint
 * on {@code photo.ml_status}.
 *
 * <ul>
 *   <li>{@code PENDING} — uploaded; not yet picked up by the orchestrator.</li>
 *   <li>{@code PROCESSING} — handed to the ML service via {@code MlClient}.</li>
 *   <li>{@code DONE} — webhook callback wrote {@code photo_student} matches.</li>
 *   <li>{@code FAILED} — ML run errored after retries.</li>
 *   <li>{@code SKIPPED} — admin/automation marked the photo as not eligible
 *       for matching (manual tagging only).</li>
 * </ul>
 */
public enum MlStatus {
    PENDING,
    PROCESSING,
    DONE,
    FAILED,
    SKIPPED
}
