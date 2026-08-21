package com.riftchallenge.leaderboard.dto;

import java.time.Instant;

/** What gets computed and cached. {@code nextRefreshAt} is derived fresh at read time instead. */
public record LeaderboardSnapshot(
        LeaderboardWindow season,
        LeaderboardWindow last7Days,
        Instant generatedAt
) {
}
