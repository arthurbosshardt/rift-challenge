package com.riftchallenge.riot;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;

class RiotRateLimitRetryInterceptorTest {

    @Test
    void parseRetryAfterMs_usesHeaderWhenPresent() {
        MockClientHttpResponse response = new MockClientHttpResponse(new byte[0], 429);
        response.getHeaders().add("Retry-After", "3");

        assertThat(RiotRateLimitRetryInterceptor.parseRetryAfterMs(response)).isEqualTo(3_000L);
    }

    @Test
    void parseRetryAfterMs_fallsBackToDefaultWhenMissing() {
        MockClientHttpResponse response = new MockClientHttpResponse(new byte[0], 429);

        assertThat(RiotRateLimitRetryInterceptor.parseRetryAfterMs(response))
                .isEqualTo(RiotRateLimitRetryInterceptor.DEFAULT_RETRY_DELAY_MS);
    }

    @Test
    void intercept_retriesOn429ThenReturnsSuccess() throws IOException {
        RiotRateLimitRetryInterceptor interceptor = new RiotRateLimitRetryInterceptor();
        MockClientHttpRequest request = new MockClientHttpRequest();
        AtomicInteger attempts = new AtomicInteger();

        ClientHttpResponse response = interceptor.intercept(
                request,
                new byte[0],
                (req, body) -> {
                    if (attempts.getAndIncrement() == 0) {
                        MockClientHttpResponse rateLimited = new MockClientHttpResponse(new byte[0], 429);
                        rateLimited.getHeaders().add(HttpHeaders.RETRY_AFTER, "0");
                        return rateLimited;
                    }
                    return new MockClientHttpResponse(new byte[0], 200);
                }
        );

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(attempts.get()).isEqualTo(2);
    }
}
