package com.riftchallenge.challenge.dto;

import com.riftchallenge.challenge.Challenge;
import com.riftchallenge.challenge.ChallengeType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ChallengeDetailResponse(
        UUID id,
        UUID shareSlug,
        String name,
        ChallengeType type,
        Instant startAt,
        Instant endAt,
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
            Instant nextRefreshAvailableAt
    ) {
        boolean isOwner = callerId != null && callerId.equals(challenge.getOwnerId());
        String status = ChallengeSummaryResponse.resolveStatus(challenge.getStartAt(), challenge.getEndAt(), now);
        boolean refreshAllowed = !"NOT_STARTED".equals(status);

        return new ChallengeDetailResponse(
                challenge.getId(),
                challenge.getShareSlug(),
                challenge.getName(),
                challenge.getType(),
                challenge.getStartAt(),
                challenge.getEndAt(),
                challenge.isPublic(),
                status,
                "/challenges/" + challenge.getShareSlug(),
                isOwner,
                lastRefreshedAt,
                nextRefreshAvailableAt,
                refreshAllowed && refreshAvailable,
                participants,
                duos
        );
    }
}
