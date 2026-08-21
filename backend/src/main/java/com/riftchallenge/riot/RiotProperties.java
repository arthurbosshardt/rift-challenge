package com.riftchallenge.riot;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "riftchallenge.riot")
public record RiotProperties(
        String apiKey,
        String appId,
        // Used only for account-v1 (Riot ID -> PUUID), which resolves globally regardless of
        // which of the 3 continental clusters is queried. Per-challenge platform/continental
        // routing lives on ChallengeRegion instead.
        String regionalRouting,
        int syncConcurrency
) {
}
