package com.riftchallenge.leaderboard.dto;

import java.time.Instant;

public record LeaderboardMatchHistoryResponse(
        String matchId,
        Integer championId,
        String championIconUrl,
        boolean win,
        int lpDelta,
        Instant playedAt
) {
}
