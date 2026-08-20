package com.riftchallenge.challenge;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class ChallengeRefreshRequestThrottle {

    static final Duration MIN_INTERVAL = Duration.ofSeconds(5);

    private final Clock clock;
    private final ConcurrentMap<String, Instant> lastRequestByClient = new ConcurrentHashMap<>();

    public ChallengeRefreshRequestThrottle(Clock clock) {
        this.clock = clock;
    }

    public void enforce(HttpServletRequest request, UUID callerId) {
        enforce(resolveClientKey(request, callerId));
    }

    void enforce(String clientKey) {
        Instant now = clock.instant();
        Instant previous = lastRequestByClient.put(clientKey, now);
        if (previous == null) {
            return;
        }

        Instant nextAllowed = previous.plus(MIN_INTERVAL);
        if (now.isBefore(nextAllowed)) {
            lastRequestByClient.put(clientKey, previous);
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Please wait before refreshing again"
            );
        }
    }

    /**
     * Authenticated callers are keyed by stable user id (not spoofable via headers).
     * Anonymous callers use the rightmost X-Forwarded-For hop (added by the edge proxy)
     * rather than the leftmost client-controlled value.
     */
    static String resolveClientKey(HttpServletRequest request, UUID callerId) {
        if (callerId != null) {
            return "user:" + callerId;
        }

        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            String[] hops = forwardedFor.split(",");
            return hops[hops.length - 1].trim();
        }

        return request.getRemoteAddr();
    }
}
