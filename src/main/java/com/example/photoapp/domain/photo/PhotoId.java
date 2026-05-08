package com.example.photoapp.domain.photo;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.UUID;

/**
 * Composite primary key for {@link Photo}. The {@code event_id} component is
 * the partition key (V1's {@code PARTITION BY HASH(event_id)}); JPA needs it
 * inside the PK because Postgres requires the partition key in the PK of a
 * partitioned table.
 *
 * <p>Implemented as a record {@code @Embeddable} — Hibernate 6+ supports this
 * directly; equals/hashCode come from the record contract.
 */
@Embeddable
public record PhotoId(
        @Column(name = "event_id", nullable = false, updatable = false) UUID eventId,
        @Column(name = "id",       nullable = false, updatable = false) UUID id) implements Serializable {
}
