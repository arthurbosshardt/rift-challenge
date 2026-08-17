package com.riftchallenge.challenge.dto;

import com.riftchallenge.challenge.ChallengeParticipant;
import java.util.List;
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
        boolean hasRankData,
        List<ParticipantMatchHistoryResponse> recentMatches
) {

    public static ParticipantProgressResponse withoutRankData(ChallengeParticipant participant, int wins, int losses) {
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
                false,
                List.of()
        );
    }

    public static ParticipantProgressResponse withRankData(
            ChallengeParticipant participant,
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
                true,
                List.of()
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
                hasRankData,
                recentMatches
        );
    }

    public ParticipantProgressResponse withRecentMatches(List<ParticipantMatchHistoryResponse> recentMatches) {
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
                hasRankData,
                recentMatches
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
