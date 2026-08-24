package com.riftchallenge.player;

import com.riftchallenge.common.ClientRequestKeys;
import com.riftchallenge.common.IntervalRequestThrottle;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Duration;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import com.riftchallenge.authentication.AuthenticatedUserIds;

/**
 * Public, unauthenticated endpoints — one of them calls the Riot API, so throttle per caller/IP.
 * Keyed per action (resolve/activity/challenges): a single page load fires all three at once,
 * and sharing one bucket across them would throttle a normal page load against itself.
 */
@Component
public class PlayerProfileRequestThrottle {

    static final Duration MIN_INTERVAL = Duration.ofSeconds(2);

    private final IntervalRequestThrottle throttle;

    public PlayerProfileRequestThrottle(Clock clock) {
        this.throttle = new IntervalRequestThrottle(
                clock,
                MIN_INTERVAL,
                "Please wait before searching for another player"
        );
    }

    public void enforce(HttpServletRequest request, Authentication authentication, String action) {
        String clientKey = ClientRequestKeys.resolve(request, AuthenticatedUserIds.optionalOwnerId(authentication));
        throttle.enforce(clientKey + ":" + action);
    }
}
