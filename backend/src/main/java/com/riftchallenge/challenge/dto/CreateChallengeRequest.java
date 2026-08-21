package com.riftchallenge.challenge.dto;

import com.riftchallenge.challenge.ChallengeType;
import com.riftchallenge.riot.ChallengeRegion;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record CreateChallengeRequest(
        @NotBlank @Size(max = 120) String name,
        @NotNull ChallengeType type,
        @NotNull ChallengeRegion region,
        @NotNull Instant startAt,
        Instant endAt,
        Integer maxGames
) {
}
