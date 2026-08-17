package com.riftchallenge.riot;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "riftchallenge.riot")
public record RiotProperties(
        String apiKey,
        String appId,
        String regionalRouting,
        String platform
) {
}
