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
        int rankScore,
        int wins,
        int losses,
        double winRate,
        Integer profileIconId,
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
                0,
                wins,
                losses,
                winRate(wins, losses),
                participant.getProfileIconId(),
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
            int rankScore,
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
                rankScore,
                wins,
                losses,
                winRate(wins, losses),
                participant.getProfileIconId(),
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
                rankScore,
                wins,
                losses,
                winRate,
                profileIconId,
                hasRankData
        );
    }

    private static double winRate(int wins, int losses) {
        int total = wins + losses;
        if (total == 0) {
            return 0.0;
        }
        return (double) wins / total;
    }
}
