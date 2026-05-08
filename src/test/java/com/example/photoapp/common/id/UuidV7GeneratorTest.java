package com.example.photoapp.common.id;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UuidV7GeneratorTest {

    private final UuidV7Generator gen = new UuidV7Generator();

    @Test
    void generates_version_7_uuids() {
        for (int i = 0; i < 1000; i++) {
            UUID id = gen.newId();
            assertThat(id.version()).as("uuid #%d", i).isEqualTo(7);
        }
    }

    @Test
    void ids_generated_sequentially_are_monotonic_by_unsigned_compare() {
        UUID prev = gen.newId();
        for (int i = 0; i < 1000; i++) {
            UUID curr = gen.newId();
            // UUIDv7 carries timestamp in the high bits; subsequent ids in the same JVM
            // (even within the same millisecond) compare strictly greater under unsigned
            // ordering thanks to the random tail being incremented monotonically.
            assertThat(unsignedCompare(prev, curr))
                    .as("monotonic: prev=%s curr=%s (iter %d)", prev, curr, i)
                    .isLessThan(0);
            prev = curr;
        }
    }

    private static int unsignedCompare(UUID a, UUID b) {
        int hi = Long.compareUnsigned(a.getMostSignificantBits(), b.getMostSignificantBits());
        if (hi != 0) return hi;
        return Long.compareUnsigned(a.getLeastSignificantBits(), b.getLeastSignificantBits());
    }
}
