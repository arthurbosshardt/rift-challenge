package com.riftchallenge.leaderboard.dto;

import java.time.Instant;
import java.util.UUID;

public record LeaderboardMatchHistoryResponse(
        String matchId,
        Integer championId,
        String championIconUrl,
        boolean win,
        int lpDelta,
        Instant playedAt,
        UUID challengeId,
        UUID participantId
) {
}
