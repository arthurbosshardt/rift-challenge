package com.riftrace.race.dto;

import jakarta.validation.constraints.NotBlank;

public record AddDuoRequest(
        @NotBlank String player1RiotId,
        @NotBlank String player2RiotId
) {
}
