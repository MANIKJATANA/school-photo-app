package com.example.photoapp.repository.photo;

import com.example.photoapp.domain.photo.MlStatus;
import com.example.photoapp.domain.photo.Photo;
import com.example.photoapp.domain.photo.PhotoId;
import com.example.photoapp.domain.photo.UploadStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.lang.reflect.Field;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * PostgreSQL-tuned implementation of {@link PhotoQueryRepository}. Holds the
 * keystone SQL — uses the ix_photo_student_student_event index for the join
 * filter and partition-prunes photo by event_id (V1's HASH partitioning,
 * ADR 0003). The only file in the codebase that contains this SQL.
 */
@Repository
public class PgPhotoQueryRepository implements PhotoQueryRepository {

    private static final String KEYSTONE_BASE = """
            SELECT p.id, p.event_id, p.school_id,
                   p.blob_key, p.blob_bucket, p.content_type, p.size_bytes,
                   p.width_px, p.height_px, p.taken_at, p.uploaded_by,
                   p.upload_status, p.ml_status, p.ml_processed_at,
                   p.created_at, p.updated_at, p.deleted_at
              FROM photo p
              JOIN photo_student ps
                ON ps.photo_id = p.id AND ps.event_id = p.event_id
             WHERE ps.student_id  = :studentId
               AND ps.event_id    = :eventId
               AND p.deleted_at IS NULL
               AND p.upload_status = 'UPLOADED'
               AND (ps.is_confirmed IS NULL OR ps.is_confirmed = TRUE)
            """;

    private static final String KEYSTONE_TAIL = """
             ORDER BY p.created_at DESC, p.id DESC
             LIMIT :limit
            """;

    private static final String CURSOR_PREDICATE = """
               AND (p.created_at < :cursorAt
                 OR (p.created_at = :cursorAt AND p.id < :cursorId))
            """;

    private final JdbcClient jdbc;

    public PgPhotoQueryRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Photo> findPhotosForStudentInEvent(UUID studentId, UUID eventId,
                                                    Instant cursorCreatedAt, UUID cursorId,
                                                    int limit) {
        StringBuilder sql = new StringBuilder(KEYSTONE_BASE);
        Map<String, Object> params = new HashMap<>();
        params.put("studentId", studentId);
        params.put("eventId", eventId);
        params.put("limit", limit);
        if (cursorCreatedAt != null && cursorId != null) {
            sql.append(CURSOR_PREDICATE);
            params.put("cursorAt", Timestamp.from(cursorCreatedAt));
            params.put("cursorId", cursorId);
        }
        sql.append(KEYSTONE_TAIL);

        return jdbc.sql(sql.toString())
                .params(params)
                .query(PHOTO_MAPPER)
                .list();
    }

    private static final RowMapper<Photo> PHOTO_MAPPER = (rs, rowNum) -> {
        Photo photo = newEmptyPhoto();
        UUID eventId = rs.getObject("event_id", UUID.class);
        UUID id = rs.getObject("id", UUID.class);
        setField(photo, "id", new PhotoId(eventId, id));
        setField(photo, "schoolId", rs.getObject("school_id", UUID.class));
        setField(photo, "blobKey", rs.getString("blob_key"));
        setField(photo, "blobBucket", rs.getString("blob_bucket"));
        setField(photo, "contentType", rs.getString("content_type"));
        setField(photo, "sizeBytes", rs.getLong("size_bytes"));
        Object widthObj = rs.getObject("width_px");
        if (widthObj != null) setField(photo, "widthPx", ((Number) widthObj).intValue());
        Object heightObj = rs.getObject("height_px");
        if (heightObj != null) setField(photo, "heightPx", ((Number) heightObj).intValue());
        Timestamp takenAt = rs.getTimestamp("taken_at");
        if (takenAt != null) setField(photo, "takenAt", takenAt.toInstant());
        setField(photo, "uploadedBy", rs.getObject("uploaded_by", UUID.class));
        setField(photo, "uploadStatus", UploadStatus.valueOf(rs.getString("upload_status")));
        setField(photo, "mlStatus", MlStatus.valueOf(rs.getString("ml_status")));
        Timestamp mlAt = rs.getTimestamp("ml_processed_at");
        if (mlAt != null) setField(photo, "mlProcessedAt", mlAt.toInstant());
        setField(photo, "createdAt", rs.getTimestamp("created_at").toInstant());
        setField(photo, "updatedAt", rs.getTimestamp("updated_at").toInstant());
        Timestamp deletedAt = rs.getTimestamp("deleted_at");
        if (deletedAt != null) setField(photo, "deletedAt", deletedAt.toInstant());
        return photo;
    };

    private static Photo newEmptyPhoto() {
        try {
            var ctor = Photo.class.getDeclaredConstructor();
            ctor.setAccessible(true);
            return ctor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Photo no-arg constructor not accessible", e);
        }
    }

    private static void setField(Object entity, String name, Object value) {
        try {
            Field f = Photo.class.getDeclaredField(name);
            f.setAccessible(true);
            f.set(entity, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Photo field " + name + " not accessible", e);
        }
    }
}
