package com.riftrace.race.dto;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record UpdateRaceEndRequest(@NotNull Instant endAt) {
}
