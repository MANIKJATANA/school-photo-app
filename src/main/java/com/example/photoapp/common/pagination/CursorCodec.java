package com.example.photoapp.common.pagination;

import java.time.Instant;
import java.util.UUID;

/**
 * Encodes / decodes the (sortKey, id) pair used as an opaque pagination cursor.
 * Listing endpoints accept and return cursors via this codec — they never
 * format cursor strings inline. The single production impl is
 * {@link Base64CursorCodec}; the interface exists so we can swap to a
 * tamper-resistant format (signed JWE, HMAC-prefixed bytes) later without
 * touching service code.
 */
public interface CursorCodec {

    record Cursor(Instant sortKey, UUID id) {}

    String encode(Cursor cursor);

    /** Returns null for null/blank input; throws Errors.BadRequest for malformed input. */
    Cursor decode(String token);
}
