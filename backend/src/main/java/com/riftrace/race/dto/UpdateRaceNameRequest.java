package com.riftrace.race.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateRaceNameRequest(@NotBlank @Size(max = 120) String name) {
}
