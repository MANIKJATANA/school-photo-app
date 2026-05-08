package com.example.photoapp.common.id;

import java.util.UUID;

/**
 * Seam over UUID generation so tests can substitute a deterministic generator.
 * Production impl is {@link UuidV7Generator}; production code injects this
 * interface, not the concrete impl.
 *
 * For static call sites that cannot use injection (e.g., utility classes,
 * @MappedSuperclass lifecycle hooks) use {@link Ids#newId()} which delegates
 * to the same UUIDv7 source.
 */
public interface IdGenerator {

    UUID newId();
}
