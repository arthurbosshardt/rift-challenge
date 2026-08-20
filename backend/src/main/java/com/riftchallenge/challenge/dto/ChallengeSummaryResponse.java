package com.riftchallenge.challenge.dto;

import com.riftchallenge.challenge.Challenge;
import com.riftchallenge.challenge.ChallengeType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ChallengeSummaryResponse(
        UUID id,
        String shareSlug,
        String name,
        ChallengeType type,
        Instant startAt,
        Instant endAt,
        Integer maxGames,
        String status,
        int entryCount,
        List<String> participantGameNames,
        List<ParticipantPreviewResponse> previewParticipants,
        List<DuoPreviewResponse> previewDuos
) {

    public static ChallengeSummaryResponse from(
            Challenge challenge,
            Instant now,
            int entryCount,
            List<String> participantGameNames,
            List<ParticipantPreviewResponse> previewParticipants,
            List<DuoPreviewResponse> previewDuos,
            boolean allParticipantsReachedMaxGames
    ) {
        return new ChallengeSummaryResponse(
                challenge.getId(),
                challenge.getShareSlug(),
                challenge.getName(),
                challenge.getType(),
                challenge.getStartAt(),
                challenge.getEndAt(),
                challenge.getMaxGames(),
                resolveStatus(
                        challenge.getStartAt(),
                        challenge.getEndAt(),
                        challenge.getMaxGames(),
                        allParticipantsReachedMaxGames,
                        now
                ),
                entryCount,
                participantGameNames,
                previewParticipants,
                previewDuos
        );
    }

    public static String resolveStatus(Instant startAt, Instant now) {
        return resolveStatus(startAt, null, now);
    }

    public static String resolveStatus(Instant startAt, Instant endAt, Instant now) {
        if (now.isBefore(startAt)) {
            return "NOT_STARTED";
        }
        if (endAt != null && !now.isBefore(endAt)) {
            return "FINISHED";
        }
        return "ACTIVE";
    }

    public static String resolveStatus(
            Instant startAt,
            Instant endAt,
            Integer maxGames,
            boolean allParticipantsReachedMaxGames,
            Instant now
    ) {
        if (now.isBefore(startAt)) {
            return "NOT_STARTED";
        }
        if (endAt != null && !now.isBefore(endAt)) {
            return "FINISHED";
        }
        if (maxGames != null && allParticipantsReachedMaxGames) {
            return "FINISHED";
        }
        return "ACTIVE";
    }
}
