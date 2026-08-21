package com.riftchallenge.challenge;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Creating a challenge, adding a participant, or adding a duo each resolve a
 * Riot account (live Riot API call). Without a per-user floor, a scripted caller
 * could burn through the shared Riot API rate limit by spamming these endpoints.
 */
@Component
public class ChallengeMutationRequestThrottle {

    static final Duration MIN_INTERVAL = Duration.ofSeconds(2);

    private final Clock clock;
    private final ConcurrentMap<UUID, Instant> lastRequestByUser = new ConcurrentHashMap<>();

    public ChallengeMutationRequestThrottle(Clock clock) {
        this.clock = clock;
    }

    public void enforce(UUID userId) {
        Instant now = clock.instant();
        Instant previous = lastRequestByUser.put(userId, now);
        if (previous == null) {
            return;
        }

        Instant nextAllowed = previous.plus(MIN_INTERVAL);
        if (now.isBefore(nextAllowed)) {
            lastRequestByUser.put(userId, previous);
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Please wait before trying again"
            );
        }
    }
}
