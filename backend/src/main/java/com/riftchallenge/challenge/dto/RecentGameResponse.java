package com.riftchallenge.challenge.dto;

import java.time.Instant;

public record RecentGameResponse(
        String id,
        String gameName,
        String tagLine,
        Integer championId,
        String championIconUrl,
        boolean win,
        Instant playedAt
) {
}
