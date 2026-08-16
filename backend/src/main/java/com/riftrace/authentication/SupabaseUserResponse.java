package com.riftrace.authentication;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import java.util.UUID;

record SupabaseUserResponse(
        UUID id,
        String email,
        @JsonProperty("user_metadata") Map<String, Object> userMetadata
) {
}
