package com.riftchallenge.challenge;

import static org.assertj.core.api.Assertions.assertThat;

import com.riftchallenge.challenge.dto.ChallengeSummaryResponse;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ChallengeSummaryResponseTest {

    @Test
    void resolveStatus_beforeStart_isNotStarted() {
        Instant startAt = Instant.parse("2026-12-01T18:00:00Z");
        Instant now = Instant.parse("2026-11-01T12:00:00Z");

        assertThat(ChallengeSummaryResponse.resolveStatus(startAt, now)).isEqualTo("NOT_STARTED");
    }

    @Test
    void resolveStatus_afterStart_isActive() {
        Instant startAt = Instant.parse("2026-01-01T18:00:00Z");
        Instant now = Instant.parse("2026-02-01T12:00:00Z");

        assertThat(ChallengeSummaryResponse.resolveStatus(startAt, now)).isEqualTo("ACTIVE");
    }

    @Test
    void resolveStatus_afterEnd_isFinished() {
        Instant startAt = Instant.parse("2026-01-01T18:00:00Z");
        Instant endAt = Instant.parse("2026-01-08T18:00:00Z");
        Instant now = Instant.parse("2026-01-09T12:00:00Z");

        assertThat(ChallengeSummaryResponse.resolveStatus(startAt, endAt, now)).isEqualTo("FINISHED");
    }

    @Test
    void resolveStatus_exactlyAtEnd_isFinished() {
        Instant startAt = Instant.parse("2026-01-01T18:00:00Z");
        Instant endAt = Instant.parse("2026-01-08T18:00:00Z");

        assertThat(ChallengeSummaryResponse.resolveStatus(startAt, endAt, endAt)).isEqualTo("FINISHED");
    }

    @Test
    void resolveStatus_maxGamesMode_notAllReached_isActive() {
        Instant startAt = Instant.parse("2026-01-01T18:00:00Z");
        Instant now = Instant.parse("2026-01-08T18:00:00Z");

        assertThat(ChallengeSummaryResponse.resolveStatus(startAt, null, 10, false, now)).isEqualTo("ACTIVE");
    }

    @Test
    void resolveStatus_maxGamesMode_allReached_isFinished() {
        Instant startAt = Instant.parse("2026-01-01T18:00:00Z");
        Instant now = Instant.parse("2026-01-08T18:00:00Z");

        assertThat(ChallengeSummaryResponse.resolveStatus(startAt, null, 10, true, now)).isEqualTo("FINISHED");
    }
}
