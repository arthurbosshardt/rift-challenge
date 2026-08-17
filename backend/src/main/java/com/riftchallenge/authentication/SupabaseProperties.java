package com.riftchallenge.authentication;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "riftchallenge.supabase")
public record SupabaseProperties(
        String url,
        String publishableKey
) {
}
