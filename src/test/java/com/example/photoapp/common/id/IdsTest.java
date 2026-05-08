package com.example.photoapp.common.id;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke test for the static facade. The contract is "any Ids.newId() returns
 * a UUIDv7"; richer guarantees (monotonicity etc.) live on the
 * {@link UuidV7Generator} contract since both share the same underlying source.
 */
class IdsTest {

    @Test
    void newId_returns_uuid_v7() {
        UUID id = Ids.newId();
        assertThat(id.version()).isEqualTo(7);
    }
}
