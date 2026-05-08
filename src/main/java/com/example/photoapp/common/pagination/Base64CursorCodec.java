package com.example.photoapp.common.pagination;

import com.example.photoapp.common.error.Errors;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.UUID;
import java.time.Instant;

/**
 * URL-safe-base64 encoded cursor. Body layout (24 bytes):
 *   <p>
 *   bytes 0..7   — sortKey epoch milliseconds (long, big-endian)
 *   bytes 8..15  — UUID most significant bits (long, big-endian)
 *   bytes 16..23 — UUID least significant bits (long, big-endian)
 *   <p>
 * No padding; URL-safe alphabet so the cursor is safe in query strings.
 */
@Component
public class Base64CursorCodec implements CursorCodec {

    private static final int BODY_LEN = Long.BYTES * 3;
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    @Override
    public String encode(Cursor cursor) {
        if (cursor == null) {
            throw new IllegalArgumentException("cursor must not be null");
        }
        ByteBuffer buf = ByteBuffer.allocate(BODY_LEN);
        buf.putLong(cursor.sortKey().toEpochMilli());
        buf.putLong(cursor.id().getMostSignificantBits());
        buf.putLong(cursor.id().getLeastSignificantBits());
        return ENCODER.encodeToString(buf.array());
    }

    @Override
    public Cursor decode(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        byte[] raw;
        try {
            raw = DECODER.decode(token);
        } catch (IllegalArgumentException e) {
            throw new Errors.BadRequest("Malformed cursor token");
        }
        if (raw.length != BODY_LEN) {
            throw new Errors.BadRequest("Malformed cursor token");
        }
        ByteBuffer buf = ByteBuffer.wrap(raw);
        long epochMilli = buf.getLong();
        long mostSig = buf.getLong();
        long leastSig = buf.getLong();
        return new Cursor(Instant.ofEpochMilli(epochMilli), new UUID(mostSig, leastSig));
    }
}
