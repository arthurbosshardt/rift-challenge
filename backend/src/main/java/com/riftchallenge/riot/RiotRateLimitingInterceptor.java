package com.riftchallenge.riot;

import java.io.IOException;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

/**
 * Innermost Riot interceptor: gates every actual HTTP attempt (including each retry from
 * {@link RiotRateLimitRetryInterceptor}, which wraps this one) behind {@link RiotAppRateLimiter},
 * then feeds the response's rate-limit headers back into it so the throttle stays matched to
 * whichever key is actually in use.
 */
public class RiotRateLimitingInterceptor implements ClientHttpRequestInterceptor {

    private final RiotAppRateLimiter rateLimiter;

    public RiotRateLimitingInterceptor(RiotAppRateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @Override
    public ClientHttpResponse intercept(
            HttpRequest request,
            byte[] body,
            ClientHttpRequestExecution execution
    ) throws IOException {
        try {
            rateLimiter.acquire();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for Riot API rate limit budget", exception);
        }

        ClientHttpResponse response = execution.execute(request, body);
        rateLimiter.onResponseHeaders(response.getHeaders());
        return response;
    }
}
