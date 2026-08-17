package com.riftchallenge.challenge.dto;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record UpdateChallengeEndRequest(@NotNull Instant endAt) {
}
