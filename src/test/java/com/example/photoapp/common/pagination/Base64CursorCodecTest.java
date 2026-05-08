package com.example.photoapp.common.pagination;

import com.example.photoapp.common.error.Errors;
import com.example.photoapp.common.id.UuidV7Generator;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Base64CursorCodecTest {

    private final Base64CursorCodec codec = new Base64CursorCodec();
    private final UuidV7Generator ids = new UuidV7Generator();

    @Test
    void round_trips_random_pairs() {
        for (int i = 0; i < 100; i++) {
            Instant t = Instant.ofEpochMilli(ThreadLocalRandom.current().nextLong(0, 4_000_000_000_000L));
            UUID id = ids.newId();
            CursorCodec.Cursor original = new CursorCodec.Cursor(t, id);

            String encoded = codec.encode(original);
            CursorCodec.Cursor decoded = codec.decode(encoded);

            assertThat(decoded).isEqualTo(original);
        }
    }

    @Test
    void encoded_cursor_is_url_safe_and_unpadded() {
        Instant t = Instant.parse("2025-01-15T10:30:00Z");
        UUID id = UUID.fromString("01900000-0000-7000-8000-000000000001");
        String token = codec.encode(new CursorCodec.Cursor(t, id));

        assertThat(token).doesNotContain("=");
        assertThat(token).doesNotContain("+");
        assertThat(token).doesNotContain("/");
    }

    @Test
    void null_or_blank_decodes_to_null() {
        assertThat(codec.decode(null)).isNull();
        assertThat(codec.decode("")).isNull();
        assertThat(codec.decode("   ")).isNull();
    }

    @Test
    void malformed_base64_throws_BadRequest() {
        assertThatThrownBy(() -> codec.decode("!!!not-base64!!!"))
                .isInstanceOf(Errors.BadRequest.class);
    }

    @Test
    void wrong_length_payload_throws_BadRequest() {
        // Valid base64 but wrong byte count (4 bytes instead of 24).
        assertThatThrownBy(() -> codec.decode("AAAAAA"))
                .isInstanceOf(Errors.BadRequest.class);
    }
}
