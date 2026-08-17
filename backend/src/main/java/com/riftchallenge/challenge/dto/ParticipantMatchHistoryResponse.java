package com.riftchallenge.challenge.dto;

import java.time.Instant;

public record ParticipantMatchHistoryResponse(
        Integer championId,
        String championIconUrl,
        boolean win,
        int lpDelta,
        Instant playedAt
) {
}
