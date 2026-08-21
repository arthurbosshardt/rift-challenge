package com.riftchallenge.leaderboard;

import java.time.Instant;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "riftchallenge.leaderboard")
public record LeaderboardProperties(
        Instant seasonStartAt,
        String adminEmail
) {
}
