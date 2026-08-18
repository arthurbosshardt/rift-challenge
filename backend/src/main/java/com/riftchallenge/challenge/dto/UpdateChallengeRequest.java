package com.riftchallenge.challenge.dto;

import jakarta.validation.constraints.Size;
import java.time.Instant;

public record UpdateChallengeRequest(
        @Size(max = 120) String name,
        Instant startAt,
        Instant endAt,
        Boolean isPublic
) {
}
