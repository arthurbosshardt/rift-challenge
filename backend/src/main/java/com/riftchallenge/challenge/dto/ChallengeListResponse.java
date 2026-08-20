package com.riftchallenge.challenge.dto;

import java.time.Instant;
import java.util.List;

public record ChallengeListResponse(
        List<ChallengeSummaryResponse> challenges,
        Instant generatedAt,
        boolean refreshAvailable,
        Instant nextRefreshAvailableAt
) {
}
