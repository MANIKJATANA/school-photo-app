package com.example.photoapp.common.time;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class ClocksTest {

    @AfterEach
    void clearOverride() {
        Clocks.clearOverride();
    }

    @Test
    void now_returns_close_to_wall_clock_when_no_override() {
        Instant before = Instant.now();
        Instant got = Clocks.now();
        Instant after = Instant.now();

        assertThat(got).isBetween(before, after);
    }

    @Test
    void setOverride_pins_now_and_clearOverride_restores() {
        Instant pinned = Instant.parse("2030-01-01T00:00:00Z");
        Clocks.setOverride(Clock.fixed(pinned, ZoneOffset.UTC));

        assertThat(Clocks.now()).isEqualTo(pinned);

        Clocks.clearOverride();
        assertThat(Clocks.now()).isAfter(Instant.parse("2026-01-01T00:00:00Z"));
    }
}
