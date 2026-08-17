package com.riftchallenge.challenge;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
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

    public void enforce(HttpServletRequest request) {
        enforce(resolveClientKey(request));
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

    static String resolveClientKey(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            int commaIndex = forwardedFor.indexOf(',');
            String client = commaIndex >= 0 ? forwardedFor.substring(0, commaIndex) : forwardedFor;
            return client.trim();
        }

        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }

        return request.getRemoteAddr();
    }
}
