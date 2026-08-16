package com.riftrace.authentication;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "riftrace.supabase")
public record SupabaseProperties(
        String url,
        String publishableKey
) {
}
