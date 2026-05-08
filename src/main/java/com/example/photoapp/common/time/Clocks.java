package com.example.photoapp.common.time;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Instant;

/**
 * Single source of truth for time access in the application.
 *
 * Two seams:
 * <ul>
 *   <li>{@link #clock()} bean — services / components inject {@link Clock} so
 *       tests can substitute {@link Clock#fixed} for deterministic time-dependent
 *       assertions. This is the default path.</li>
 *   <li>{@link #now()} static facade — for sites that cannot use injection
 *       (JPA {@code @PrePersist}/{@code @PreUpdate} hooks, static utilities).
 *       Tests that need to override this can use {@link #setOverride} from a
 *       test-only setup.</li>
 * </ul>
 */
@Configuration
public class Clocks {

    private static volatile Clock OVERRIDE;

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    /** Used by JPA lifecycle hooks. Honours a test-only override if set, otherwise returns wall-clock UTC now. */
    public static Instant now() {
        Clock o = OVERRIDE;
        return o == null ? Instant.now(Clock.systemUTC()) : o.instant();
    }

    /** Test-only: pin the static facade to a fixed clock. Production code never calls this. */
    public static void setOverride(Clock clock) {
        OVERRIDE = clock;
    }

    /** Test-only: clear any override set by {@link #setOverride}. */
    public static void clearOverride() {
        OVERRIDE = null;
    }
}
