package com.riftrace.race.dto;

import com.riftrace.race.RaceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record CreateRaceRequest(
        @NotBlank @Size(max = 120) String name,
        @NotNull RaceType type,
        @NotNull Instant startAt,
        boolean isPublic
) {
}
