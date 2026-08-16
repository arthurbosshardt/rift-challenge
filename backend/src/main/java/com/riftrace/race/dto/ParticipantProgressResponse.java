package com.riftrace.race.dto;

import com.riftrace.race.RaceParticipant;
import java.util.UUID;

public record ParticipantProgressResponse(
        UUID id,
        String gameName,
        String tagLine,
        String riotId,
        int position,
        String currentTier,
        String currentRank,
        int currentLp,
        int lpGained,
        int wins,
        int losses,
        boolean hasRankData
) {

    public static ParticipantProgressResponse withoutRankData(RaceParticipant participant, int wins, int losses) {
        return new ParticipantProgressResponse(
                participant.getId(),
                participant.getRiotGameName(),
                participant.getRiotTagLine(),
                participant.getRiotGameName() + "#" + participant.getRiotTagLine(),
                0,
                null,
                null,
                0,
                0,
                wins,
                losses,
                false
        );
    }

    public static ParticipantProgressResponse withRankData(
            RaceParticipant participant,
            int position,
            String currentTier,
            String currentRank,
            int currentLp,
            int lpGained,
            int wins,
            int losses
    ) {
        return new ParticipantProgressResponse(
                participant.getId(),
                participant.getRiotGameName(),
                participant.getRiotTagLine(),
                participant.getRiotGameName() + "#" + participant.getRiotTagLine(),
                position,
                currentTier,
                currentRank,
                currentLp,
                lpGained,
                wins,
                losses,
                true
        );
    }

    public ParticipantProgressResponse withPosition(int position) {
        return new ParticipantProgressResponse(
                id,
                gameName,
                tagLine,
                riotId,
                position,
                currentTier,
                currentRank,
                currentLp,
                lpGained,
                wins,
                losses,
                hasRankData
        );
    }
}
