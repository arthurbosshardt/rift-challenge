package com.riftchallenge.riot;

import java.io.IOException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

public class RiotRateLimitRetryInterceptor implements ClientHttpRequestInterceptor {

    private static final Logger log = LoggerFactory.getLogger(RiotRateLimitRetryInterceptor.class);

    static final int MAX_ATTEMPTS = 5;
    static final long DEFAULT_RETRY_DELAY_MS = 2_000L;
    /** Used only by the no-arg constructor (tests, or any caller not wiring RiotProperties). */
    static final long DEFAULT_MAX_RETRY_DELAY_MS = 10_000L;

    private final long maxRetryDelayMs;

    public RiotRateLimitRetryInterceptor() {
        this(DEFAULT_MAX_RETRY_DELAY_MS);
    }

    public RiotRateLimitRetryInterceptor(long maxRetryDelayMs) {
        this.maxRetryDelayMs = maxRetryDelayMs;
    }

    @Override
    public ClientHttpResponse intercept(
            HttpRequest request,
            byte[] body,
            ClientHttpRequestExecution execution
    ) throws IOException {
        ClientHttpResponse response = null;

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            if (response != null) {
                response.close();
            }

            response = execution.execute(request, body);
            if (response.getStatusCode().value() != 429 || attempt == MAX_ATTEMPTS) {
                return response;
            }

            long delayMs = parseRetryAfterMs(response, maxRetryDelayMs);
            log.warn(
                    "Riot API rate limit for {} (attempt {}/{}), retrying in {}ms",
                    request.getURI(),
                    attempt,
                    MAX_ATTEMPTS,
                    delayMs
            );
            sleep(delayMs);
        }

        return response;
    }

    /**
     * @param maxDelayMs upper bound applied to both the header-supplied and default delays — Riot
     *     can ask for a Retry-After well beyond what's reasonable to block a request thread on.
     */
    static long parseRetryAfterMs(ClientHttpResponse response, long maxDelayMs) {
        List<String> retryAfter = response.getHeaders().get("Retry-After");
        if (retryAfter != null && !retryAfter.isEmpty()) {
            try {
                long requestedMs = Long.parseLong(retryAfter.getFirst()) * 1_000L;
                return Math.min(maxDelayMs, Math.max(1_000L, requestedMs));
            } catch (NumberFormatException ignored) {
                // Fall back to default delay.
            }
        }
        return Math.min(maxDelayMs, DEFAULT_RETRY_DELAY_MS);
    }

    private static void sleep(long delayMs) throws IOException {
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for Riot API rate limit", exception);
        }
    }
}
