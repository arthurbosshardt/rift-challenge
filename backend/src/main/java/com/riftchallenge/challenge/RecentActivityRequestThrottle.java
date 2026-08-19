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
 * GET /api/challenges/recent fans out to dozens of live Riot API calls per user
 * (rank + match list + match detail, per linked account). Unlike challenge refresh,
 * it has no persisted data to fall back on, so it needs its own, longer-than-UX,
 * server-enforced cooldown to avoid burning the shared Riot API rate limit.
 */
@Component
public class RecentActivityRequestThrottle {

    static final Duration MIN_INTERVAL = Duration.ofSeconds(10);

    private final Clock clock;
    private final ConcurrentMap<UUID, Instant> lastRequestByUser = new ConcurrentHashMap<>();

    public RecentActivityRequestThrottle(Clock clock) {
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
                    "Please wait before refreshing your recent activity again"
            );
        }
    }
}
