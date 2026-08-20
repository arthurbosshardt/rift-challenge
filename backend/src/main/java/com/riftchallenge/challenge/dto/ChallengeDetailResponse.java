package com.riftchallenge.challenge.dto;

import com.riftchallenge.challenge.Challenge;
import com.riftchallenge.challenge.ChallengeSharePaths;
import com.riftchallenge.challenge.ChallengeType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ChallengeDetailResponse(
        UUID id,
        String shareSlug,
        String name,
        ChallengeType type,
        Instant startAt,
        Instant endAt,
        Integer maxGames,
        boolean isPublic,
        String status,
        String sharePath,
        boolean isOwner,
        Instant lastRefreshedAt,
        Instant nextRefreshAvailableAt,
        boolean refreshAvailable,
        List<ParticipantProgressResponse> participants,
        List<DuoProgressResponse> duos
) {

    public static ChallengeDetailResponse from(
            Challenge challenge,
            Instant now,
            List<ParticipantProgressResponse> participants,
            List<DuoProgressResponse> duos,
            UUID callerId,
            Instant lastRefreshedAt,
            boolean refreshAvailable,
            Instant nextRefreshAvailableAt,
            boolean allParticipantsReachedMaxGames
    ) {
        boolean isOwner = callerId != null && callerId.equals(challenge.getOwnerId());
        String status = ChallengeSummaryResponse.resolveStatus(
                challenge.getStartAt(),
                challenge.getEndAt(),
                challenge.getMaxGames(),
                allParticipantsReachedMaxGames,
                now
        );
        boolean refreshAllowed = !"NOT_STARTED".equals(status);

        return new ChallengeDetailResponse(
                challenge.getId(),
                challenge.getShareSlug(),
                challenge.getName(),
                challenge.getType(),
                challenge.getStartAt(),
                challenge.getEndAt(),
                challenge.getMaxGames(),
                challenge.isPublic(),
                status,
                ChallengeSharePaths.buildSharePath(challenge.getShareSlug()),
                isOwner,
                lastRefreshedAt,
                nextRefreshAvailableAt,
                refreshAllowed && refreshAvailable,
                participants,
                duos
        );
    }
}
