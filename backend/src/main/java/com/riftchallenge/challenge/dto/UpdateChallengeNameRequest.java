package com.riftchallenge.challenge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateChallengeNameRequest(@NotBlank @Size(max = 120) String name) {
}
