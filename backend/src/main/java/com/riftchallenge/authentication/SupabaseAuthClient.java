package com.riftchallenge.authentication;

import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SupabaseAuthClient {

    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final int READ_TIMEOUT_MS = 8_000;

    private final RestClient restClient;
    private final SupabaseProperties properties;

    public SupabaseAuthClient(SupabaseProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT_MS);
        requestFactory.setReadTimeout(READ_TIMEOUT_MS);
        this.restClient = RestClient.builder()
                .baseUrl(properties.url())
                .defaultHeader("apikey", properties.publishableKey())
                .requestFactory(requestFactory)
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
