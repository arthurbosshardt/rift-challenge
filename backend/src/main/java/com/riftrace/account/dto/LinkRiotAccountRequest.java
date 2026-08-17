package com.riftrace.account.dto;

import jakarta.validation.constraints.NotBlank;

public record LinkRiotAccountRequest(@NotBlank String riotId) {
}
