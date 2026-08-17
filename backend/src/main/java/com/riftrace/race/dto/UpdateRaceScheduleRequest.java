package com.riftrace.race.dto;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record UpdateRaceScheduleRequest(
        @NotNull Instant startAt,
        @NotNull Instant endAt
) {
}
