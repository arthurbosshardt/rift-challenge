package com.riftrace.race.dto;

import java.util.UUID;

public record DuoPreviewResponse(
        UUID id,
        ParticipantPreviewResponse player1,
        ParticipantPreviewResponse player2,
        int combinedLpGained,
        int wins,
        int losses,
        double winRate,
        int position,
        boolean eligible
) {

    public static DuoPreviewResponse from(DuoProgressResponse duo) {
        return new DuoPreviewResponse(
                duo.id(),
                ParticipantPreviewResponse.from(duo.player1()),
                ParticipantPreviewResponse.from(duo.player2()),
                duo.combinedLpGained(),
                duo.wins(),
                duo.losses(),
                duo.winRate(),
                duo.position(),
                duo.eligible()
        );
    }
}
