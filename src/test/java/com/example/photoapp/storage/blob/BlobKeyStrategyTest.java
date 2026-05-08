package com.example.photoapp.storage.blob;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BlobKeyStrategyTest {

    private static final UUID SCHOOL = UUID.fromString("01900000-0000-7000-8000-000000000001");
    private static final UUID EVENT  = UUID.fromString("01900000-0000-7000-8000-000000000002");
    private static final UUID PHOTO  = UUID.fromString("01900000-0000-7000-8000-000000000003");

    @Test
    void photo_key_layout() {
        String key = BlobKeyStrategy.photoKey(SCHOOL, EVENT, PHOTO, "jpg");
        assertThat(key).isEqualTo("schools/" + SCHOOL + "/events/" + EVENT + "/" + PHOTO + ".jpg");
    }

    @Test
    void common_extensions_accepted() {
        for (String ext : new String[]{"jpg", "jpeg", "png", "heic", "webp"}) {
            assertThat(BlobKeyStrategy.photoKey(SCHOOL, EVENT, PHOTO, ext)).endsWith("." + ext);
        }
    }

    @Test
    void extension_with_slash_rejected() {
        assertThatThrownBy(() -> BlobKeyStrategy.photoKey(SCHOOL, EVENT, PHOTO, "../etc/passwd"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void extension_with_dot_rejected() {
        assertThatThrownBy(() -> BlobKeyStrategy.photoKey(SCHOOL, EVENT, PHOTO, "tar.gz"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void uppercase_extension_rejected() {
        assertThatThrownBy(() -> BlobKeyStrategy.photoKey(SCHOOL, EVENT, PHOTO, "JPG"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void empty_extension_rejected() {
        assertThatThrownBy(() -> BlobKeyStrategy.photoKey(SCHOOL, EVENT, PHOTO, ""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void null_extension_rejected() {
        assertThatThrownBy(() -> BlobKeyStrategy.photoKey(SCHOOL, EVENT, PHOTO, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void overly_long_extension_rejected() {
        assertThatThrownBy(() -> BlobKeyStrategy.photoKey(SCHOOL, EVENT, PHOTO, "abcdefghi"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void null_uuid_rejected() {
        assertThatThrownBy(() -> BlobKeyStrategy.photoKey(null, EVENT, PHOTO, "jpg"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
