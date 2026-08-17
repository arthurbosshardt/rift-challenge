package com.riftchallenge.challenge.dto;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record UpdateChallengeStartRequest(@NotNull Instant startAt) {
}
