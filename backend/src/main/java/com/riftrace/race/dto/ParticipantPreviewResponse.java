package com.riftrace.race.dto;

import com.riftrace.race.RaceParticipant;
import java.util.UUID;

public record ParticipantPreviewResponse(
        UUID id,
        String gameName,
        String tagLine,
        String riotId,
        Integer profileIconId,
        String currentTier,
        String currentRank,
        int currentLp,
        int lpGained,
        int wins,
        int losses,
        double winRate,
        int position
) {

    public static ParticipantPreviewResponse from(ParticipantProgressResponse progress) {
        return new ParticipantPreviewResponse(
                progress.id(),
                progress.gameName(),
                progress.tagLine(),
                progress.riotId(),
                progress.profileIconId(),
                progress.currentTier(),
                progress.currentRank(),
                progress.currentLp(),
                progress.lpGained(),
                progress.wins(),
                progress.losses(),
                progress.winRate(),
                progress.position()
        );
    }

    public static ParticipantPreviewResponse fromParticipant(RaceParticipant participant, int position) {
        return new ParticipantPreviewResponse(
                participant.getId(),
                participant.getRiotGameName(),
                participant.getRiotTagLine(),
                participant.getRiotGameName() + "#" + participant.getRiotTagLine(),
                participant.getProfileIconId(),
                null,
                null,
                0,
                0,
                0,
                0,
                0.0,
                position
        );
    }
}
