package com.riftchallenge.leaderboard.dto;

import java.time.Instant;
import java.time.ZoneOffset;

public record LeaderboardResponse(
        LeaderboardWindow season,
        LeaderboardWindow last7Days,
        Instant generatedAt,
        Instant nextRefreshAt,
        boolean canRefresh,
        int seasonYear
) {
    public static LeaderboardResponse from(
            LeaderboardSnapshot snapshot,
            Instant nextRefreshAt,
            boolean canRefresh,
            Instant seasonStartAt
    ) {
        int seasonYear = seasonStartAt.atZone(ZoneOffset.UTC).getYear();
        return new LeaderboardResponse(
                snapshot.season(),
                snapshot.last7Days(),
                snapshot.generatedAt(),
                nextRefreshAt,
                canRefresh,
                seasonYear
        );
    }
}
