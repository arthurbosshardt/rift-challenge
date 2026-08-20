package com.riftchallenge.authentication;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import java.util.UUID;

record SupabaseUserResponse(
        UUID id,
        String email,
        @JsonProperty("email_confirmed_at") String emailConfirmedAt,
        @JsonProperty("user_metadata") Map<String, Object> userMetadata
) {
    boolean hasConfirmedEmail() {
        return emailConfirmedAt != null && !emailConfirmedAt.isBlank();
    }
}
