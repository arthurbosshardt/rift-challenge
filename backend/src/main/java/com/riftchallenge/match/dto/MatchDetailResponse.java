package com.riftchallenge.match.dto;

import java.time.Instant;
import java.util.List;

public record MatchDetailResponse(
        String matchId,
        Instant playedAt,
        long durationSeconds,
        boolean win,
        List<MatchParticipantResponse> myTeam,
        List<MatchParticipantResponse> enemyTeam
) {
}
