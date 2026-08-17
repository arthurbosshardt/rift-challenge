package com.riftchallenge.challenge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddParticipantRequest(
        @NotBlank @Size(max = 22) String riotId
) {
}
