package com.riftrace.race.dto;

import java.util.UUID;

public record DuoProgressResponse(
        UUID id,
        ParticipantProgressResponse player1,
        ParticipantProgressResponse player2,
        int combinedRankScore,
        int combinedLpGained,
        int wins,
        int losses,
        double winRate,
        boolean eligible,
        String ineligibilityReason,
        int position
) {

    public DuoProgressResponse withPosition(int position) {
        return new DuoProgressResponse(
                id,
                player1,
                player2,
                combinedRankScore,
                combinedLpGained,
                wins,
                losses,
                winRate,
                eligible,
                ineligibilityReason,
                position
        );
    }
}
