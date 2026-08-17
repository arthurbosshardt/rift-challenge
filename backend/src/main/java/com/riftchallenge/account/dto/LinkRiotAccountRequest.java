package com.riftchallenge.account.dto;

import jakarta.validation.constraints.NotBlank;

public record LinkRiotAccountRequest(
        @NotBlank String riotId,
        boolean smurf
) {
    public LinkRiotAccountRequest {
        // default smurf=false for JSON clients that omit the field
    }

    public LinkRiotAccountRequest(String riotId) {
        this(riotId, false);
    }
}
