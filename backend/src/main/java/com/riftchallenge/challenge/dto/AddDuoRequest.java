package com.riftchallenge.challenge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddDuoRequest(
        @NotBlank @Size(max = 22) String player1RiotId,
        @NotBlank @Size(max = 22) String player2RiotId
) {
}
