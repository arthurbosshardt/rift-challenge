package com.riftchallenge.challenge.dto;

import java.time.Instant;

public record DuoMatchHistoryResponse(
        String matchId,
        boolean win,
        int player1ChampionId,
        String player1ChampionIconUrl,
        int player1LpDelta,
        int player2ChampionId,
        String player2ChampionIconUrl,
        int player2LpDelta,
        Instant playedAt
) {
}
