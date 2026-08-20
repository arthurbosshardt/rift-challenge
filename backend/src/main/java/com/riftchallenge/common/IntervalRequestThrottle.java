package com.riftchallenge.common;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Simple per-key minimum-interval throttle shared by public/expensive endpoints.
 */
public class IntervalRequestThrottle {

    private final Clock clock;
    private final Duration minInterval;
    private final String tooManyRequestsMessage;
    private final ConcurrentMap<String, Instant> lastRequestByKey = new ConcurrentHashMap<>();

    public IntervalRequestThrottle(Clock clock, Duration minInterval, String tooManyRequestsMessage) {
        this.clock = clock;
        this.minInterval = minInterval;
        this.tooManyRequestsMessage = tooManyRequestsMessage;
    }

    public void enforce(String key) {
        Instant now = clock.instant();
        Instant previous = lastRequestByKey.put(key, now);
        if (previous == null) {
            return;
        }

        Instant nextAllowed = previous.plus(minInterval);
        if (now.isBefore(nextAllowed)) {
            lastRequestByKey.put(key, previous);
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, tooManyRequestsMessage);
        }
    }
}
