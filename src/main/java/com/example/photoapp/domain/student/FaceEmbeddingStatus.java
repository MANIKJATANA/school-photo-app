package com.example.photoapp.domain.student;

/**
 * ML face-embedding lifecycle on {@link Student}. Mirrors the V1 CHECK
 * constraint on {@code student.face_embedding_status}.
 */
public enum FaceEmbeddingStatus {
    PENDING,
    ENROLLED,
    FAILED
}
