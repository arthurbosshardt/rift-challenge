package com.riftchallenge.authentication;

import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SupabaseAuthClient {

    private final RestClient restClient;
    private final SupabaseProperties properties;

    public SupabaseAuthClient(SupabaseProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.builder()
                .baseUrl(properties.url())
                .defaultHeader("apikey", properties.publishableKey())
                .build();
    }

    public Optional<SupabaseUserResponse> fetchUser(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            return Optional.empty();
        }

        try {
            SupabaseUserResponse user = restClient.get()
                    .uri("/auth/v1/user")
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .body(SupabaseUserResponse.class);
            return Optional.ofNullable(user);
        } catch (HttpClientErrorException exception) {
            if (exception.getStatusCode() == HttpStatus.UNAUTHORIZED
                    || exception.getStatusCode() == HttpStatus.FORBIDDEN) {
                return Optional.empty();
            }
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Supabase auth validation failed"
            );
        }
    }
}
