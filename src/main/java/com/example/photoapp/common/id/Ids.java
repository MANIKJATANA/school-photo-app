package com.example.photoapp.common.id;

import com.github.f4b6a3.uuid.UuidCreator;

import java.util.UUID;

/**
 * Static facade for UUIDv7 generation. Use this only at sites that cannot use
 * dependency injection (JPA lifecycle hooks, static utilities, tests'
 * one-shot fixtures). Service / component code MUST inject {@link IdGenerator}
 * instead so tests can swap in a deterministic generator.
 */
public final class Ids {

    private Ids() {}

    public static UUID newId() {
        return UuidCreator.getTimeOrderedEpoch();
    }
}
