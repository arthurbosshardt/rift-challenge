package com.riftchallenge.challenge.dto;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record UpdateChallengeScheduleRequest(
        @NotNull Instant startAt,
        @NotNull Instant endAt
) {
}
