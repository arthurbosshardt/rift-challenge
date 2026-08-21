package com.riftchallenge.leaderboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class LeaderboardCacheServiceTest {

    @Test
    void nextRefreshAt_picksTheNextHourlyMarkSameDay() {
        Instant now = Instant.parse("2026-08-21T10:00:00Z");
        assertThat(LeaderboardCacheService.nextRefreshAt(now)).isEqualTo(Instant.parse("2026-08-21T12:00:00Z"));
    }

    @Test
    void nextRefreshAt_rightAtAMark_picksTheNextOne() {
        Instant now = Instant.parse("2026-08-21T12:00:00Z");
        assertThat(LeaderboardCacheService.nextRefreshAt(now)).isEqualTo(Instant.parse("2026-08-21T18:00:00Z"));
    }

    @Test
    void nextRefreshAt_afterLastMarkOfTheDay_rollsOverToMidnight() {
        Instant now = Instant.parse("2026-08-21T19:30:00Z");
        assertThat(LeaderboardCacheService.nextRefreshAt(now)).isEqualTo(Instant.parse("2026-08-22T00:00:00Z"));
    }
}
