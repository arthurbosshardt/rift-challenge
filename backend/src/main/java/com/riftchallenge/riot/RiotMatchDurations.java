package com.riftchallenge.riot;

/**
 * Riot match-v5 {@code gameDuration}: seconds since patch 11.20, milliseconds before.
 */
public final class RiotMatchDurations {

    static final long LEGACY_MILLISECONDS_THRESHOLD = 10_000L;

    private RiotMatchDurations() {
    }

    public static long normalizeSeconds(long rawDuration) {
        if (rawDuration <= 0) {
            return 0L;
        }
        if (rawDuration > LEGACY_MILLISECONDS_THRESHOLD) {
            return rawDuration / 1_000L;
        }
        return rawDuration;
    }
}
