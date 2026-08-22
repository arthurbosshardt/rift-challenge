package com.riftchallenge.challenge;

import com.riftchallenge.challenge.dto.DuoProgressResponse;
import com.riftchallenge.challenge.dto.ParticipantProgressResponse;
import java.util.Comparator;

final class ChallengeLeaderboardOrdering {

    static final Comparator<ParticipantProgressResponse> BY_LP_GAIN =
            Comparator.comparingInt(ParticipantProgressResponse::lpGained).reversed()
                    .thenComparing(Comparator.comparingDouble(ParticipantProgressResponse::winRate).reversed())
                    .thenComparing(Comparator.comparingInt(ParticipantProgressResponse::wins).reversed())
                    .thenComparing(Comparator.comparingInt(ParticipantProgressResponse::rankScore).reversed())
                    .thenComparing(ParticipantProgressResponse::riotId, String.CASE_INSENSITIVE_ORDER);

    static final Comparator<DuoProgressResponse> BY_LP_GAIN_DUO =
            Comparator.comparing(DuoProgressResponse::eligible).reversed()
                    .thenComparing(Comparator.comparingInt(DuoProgressResponse::combinedLpGained).reversed())
                    .thenComparing(Comparator.comparingDouble(DuoProgressResponse::winRate).reversed())
                    .thenComparing(Comparator.comparingInt(DuoProgressResponse::wins).reversed())
                    .thenComparing(Comparator.comparingInt(DuoProgressResponse::combinedRankScore).reversed())
                    .thenComparing(duo -> duo.id().toString());

    private ChallengeLeaderboardOrdering() {
    }
}
