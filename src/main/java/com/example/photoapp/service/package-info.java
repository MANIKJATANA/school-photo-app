/**
 * Orchestration layer. Composes repositories, security policy, storage, and
 * external clients to deliver use cases.
 *
 * <p>Key services include {@code PhotoUploadService}, {@code TaggingService},
 * {@code StudentEventRefresher} (maintains the {@code student_event}
 * precompute in the same transaction as {@code photo_student} writes —
 * see ADR 0004), {@code MlOrchestrator}, {@code ShareService},
 * {@code NotificationService}, {@code OutboxRelay}.
 *
 * <p>No PostgreSQL-specific syntax lives here. No raw {@code S3Client},
 * {@code RestClient} or HMAC math — those live behind their respective
 * abstractions ({@code BlobStore}, {@code MlClient}, etc.).
 */
package com.example.photoapp.service;
